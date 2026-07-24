package com.career.recommendation.service;

import com.career.recommendation.dto.gemini.GeminiRecommendationResult;
import com.career.recommendation.dto.gemini.GeminiRecommendationResult.GeminiActivity;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.MatchScoreCalculator;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.RecommendationFallbackData;
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BE-1 담당 — F-03 활동 추천 비즈니스 로직.
 *
 * 캐시 전략: 유저당 1건, 24시간 만료.
 * Gemini 실패 처리: 1회 재시도 → 2회 연속 실패 시 Fallback 데이터 반환 + isAiRecommendation=false.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCacheService recommendationCacheService;
    private final ActivityRepository activityRepository;
    private final SimilarSpecFinder similarSpecFinder;
    private final MatchScoreCalculator matchScoreCalculator;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final ObjectMapper objectMapper;

    private static final int CACHE_HOURS = 24;

    /**
     * 현재 로그인한 유저의 맞춤 추천 활동 목록을 반환한다.
     * 유효한 캐시가 있으면 DB에서 즉시 반환한다.
     */
    @Transactional
    public RecommendationResponse getRecommendations(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        // 1. 유효 캐시 확인 (활동 목록이 포함된 정상 캐시만 사용)
        Recommendation cached = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
        if (cached != null && cached.isValid()) {
            RecommendationResponse deserialized = deserialize(cached.getResultJson());
            if (deserialized != null && deserialized.getActivities() != null && !deserialized.getActivities().isEmpty()) {
                return deserialized;
            }
        }

        // 2. 스펙 및 목표 직무 조회
        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        // 3. 유사 합격자 검색 (SimilarSpecFinder)
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        List<PasserData> similarPassers = similarSpecFinder.find(
                jobType,
                (userSpec != null) ? userSpec.getGpa() : null
        );
        String comparisonMessage = similarSpecFinder.buildComparisonMessage(similarPassers.size(), jobType);

        // 4. DB 활성 활동 목록 조회 (RAG 패턴 — Gemini에 선택지 제공)
        List<Activity> activeActivities = activityRepository.findByIsActiveTrue();
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJson(activeActivities);

        // 5. Gemini API 호출 (최대 2회 시도)
        String userSpecJson = promptDataBuilder.serializeSpecForRecommendation(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        String similarCasesStr = promptDataBuilder.buildSimilarCasesText(similarPassers);

        RecommendationResponse response = callGeminiWithRetry(
                userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson,
                userSpec, similarPassers, comparisonMessage, activeActivities
        );

        // 6. 결과 캐싱 (24시간) — 별도 Bean에서 호출해야 @Transactional 프록시가 정상 동작함
        recommendationCacheService.save(user, response, CACHE_HOURS);

        return response;
    }

    /**
     * Gemini API를 호출하고 JSON 파싱을 시도한다. 실패 시 1회 재시도 후 Fallback 반환.
     */
    private RecommendationResponse callGeminiWithRetry(
            String userSpecJson, String targetJobStr, String similarCasesStr, String availableActivitiesJson,
            UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage,
            List<Activity> activeActivities) {

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRecommendation(
                        userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson);
                if (rawJson.isBlank()) {
                    log.warn("Gemini 추천 응답 비어있음 (시도 {}회)", attempt);
                    continue;
                }

                RecommendationResponse parsed = parseGeminiResponse(
                        rawJson, userSpec, similarPassers, comparisonMessage, activeActivities);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("Gemini 추천 파싱 실패 (시도 {}회): {}", attempt, e.getMessage());
            }
        }

        log.info("Gemini 추천 미사용/실패 → DB 저장 활동 기반 맞춤 추천 반환");
        return buildDbFallbackRecommendation(userSpec, similarPassers, comparisonMessage, activeActivities);
    }

    private RecommendationResponse buildDbFallbackRecommendation(
            UserSpec userSpec, List<PasserData> similarPassers,
            String comparisonMessage, List<Activity> activeActivities) {

        int overallMatchScore = matchScoreCalculator.calculate(userSpec, similarPassers);
        List<ActivityRecommendation> recs = new ArrayList<>();

        int count = Math.min(5, activeActivities.size());
        for (int i = 0; i < count; i++) {
            Activity a = activeActivities.get(i);
            recs.add(ActivityRecommendation.builder()
                    .id(a.getId())
                    .type(a.getType())
                    .name(a.getName())
                    .reason(a.getDescription() != null && !a.getDescription().isBlank() 
                            ? a.getDescription() 
                            : "사용자의 목표 직무 및 학점 스펙 기반 DB 맞춤 추천 활동입니다.")
                    .deadline(a.getDeadline())
                    .build());
        }

        return RecommendationResponse.builder()
                .activities(recs)
                .matchScore(overallMatchScore)
                .comparisonMessage(comparisonMessage)
                .isAiRecommendation(false)
                .build();
    }

    /**
     * Gemini 응답 JSON을 RecommendationResponse DTO로 변환한다.
     * 타입 안전한 GeminiRecommendationResult DTO로 파싱하고,
     * DB에 실재하는 활동만 포함하며, matchScore를 주입한다.
     */
    private RecommendationResponse parseGeminiResponse(
            String rawJson, UserSpec userSpec, List<PasserData> similarPassers,
            String comparisonMessage, List<Activity> activeActivities) throws Exception {

        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        // 타입 안전한 DTO로 파싱 (개선 #5)
        GeminiRecommendationResult geminiResult = objectMapper.readValue(rawJson, GeminiRecommendationResult.class);

        if (geminiResult.getActivities() == null || geminiResult.getActivities().isEmpty()) return null;

        int overallMatchScore = matchScoreCalculator.calculate(userSpec, similarPassers);

        List<ActivityRecommendation> result = new ArrayList<>();
        for (GeminiActivity a : geminiResult.getActivities()) {
            // Gemini가 반환한 ID를 UUID로 파싱
            UUID activityId = null;
            if (a.getId() != null) {
                try { activityId = UUID.fromString(a.getId()); } catch (Exception ignored) {}
            }

            // DB에 실재하는 활동만 포함 (할루시네이션 방지)
            if (activityId != null && activityMap.containsKey(activityId)) {
                Activity dbActivity = activityMap.get(activityId);
                result.add(ActivityRecommendation.builder()
                        .id(dbActivity.getId())
                        .type(dbActivity.getType())
                        .name(dbActivity.getName())
                        .reason(a.getReason() != null ? a.getReason() : "")
                        .deadline(dbActivity.getDeadline())
                        .build());
            } else {
                log.warn("Gemini가 DB에 없는 활동 ID를 반환함 (무시): {}", a.getId());
            }
        }

        if (result.isEmpty()) return null;

        return RecommendationResponse.builder()
                .activities(result)
                .matchScore(overallMatchScore)
                .comparisonMessage(comparisonMessage)
                .isAiRecommendation(true)
                .build();
    }

    private RecommendationResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, RecommendationResponse.class);
        } catch (Exception e) {
            log.warn("캐시 역직렬화 실패 → 재생성: {}", e.getMessage());
            return null;
        }
    }
}
