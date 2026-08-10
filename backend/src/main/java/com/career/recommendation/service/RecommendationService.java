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
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.MatchScoreCalculator;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BE-1 담당 — F-03 활동 추천 비즈니스 로직.
 *
 * 캐시 전략: 유저당 1건, 스펙 변경 시 즉시 갱신 (하루 최대 3회).
 * Gemini 실패 처리: 1회 재시도(2초 백오프) → 2회 연속 실패 시 Fallback 데이터 반환 + isAiRecommendation=false.
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
    private final PasserDataRepository passerDataRepository;
    private final SimilarSpecFinder similarSpecFinder;
    private final MatchScoreCalculator matchScoreCalculator;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final ObjectMapper objectMapper;

    private static final int MAX_RECOMMENDABLE_ACTIVITIES = 20;
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    /**
     * 현재 로그인한 유저의 맞춤 추천 활동 목록을 반환한다.
     * 유효한 캐시가 있으면 DB에서 즉시 반환한다.
     */
    @Transactional
    public RecommendationResponse getRecommendations(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);

        // 1. 유효 캐시 확인 및 업데이트 필요 여부 판별
        Recommendation cached = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        boolean needsNewAiCall = false;
        RecommendationResponse cachedResponse = null;

        if (cached == null) {
            needsNewAiCall = true;
        } else {
            cachedResponse = deserialize(cached.getResultJson());
            boolean isSpecChanged = isSpecModifiedSince(userSpec, targetJob, cached.getCreatedAt());
            boolean hasUsableActivities = hasUsableCachedActivities(cachedResponse, today);
            boolean isLegacyCache = cachedResponse == null
                    || cachedResponse.getSampleComparisonData() == null
                    || cachedResponse.getScoreFormulaVersion() == null
                    || cachedResponse.getScoreFormulaVersion() < MatchScoreCalculator.CURRENT_SCORE_FORMULA_VERSION;

            if (isLegacyCache) {
                needsNewAiCall = true; // 출처 표시 필드가 없는 구버전 캐시는 한 번 재생성
            } else if (!hasUsableActivities || isSpecChanged) {
                // 활동 만료 또는 스펙 변경으로 갱신이 필요하지만, 하루 제한(3회) 내인지 확인한다.
                // 이 체크가 없으면 Gemini가 장애로 폴백만 반환할 때(캐시 미저장) 만료 활동이
                // 계속 남아 매 요청마다 API를 호출하게 된다(RoadmapService와 동일한 안전장치).
                if (cached.getLastUpdatedDate() == null || !today.equals(cached.getLastUpdatedDate())) {
                    needsNewAiCall = true;
                } else if (cached.getDailyUpdateCount() < 3) {
                    needsNewAiCall = true;
                }
            }
        }

        if (!needsNewAiCall && cachedResponse != null) {
            return cachedResponse;
        }

        // 3. 유사 합격자 검색 (SimilarSpecFinder)
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        List<PasserData> similarPassers = similarSpecFinder.find(
                jobType,
                (userSpec != null) ? userSpec.getGpa() : null,
                (userSpec != null) ? userSpec.getGpaMax() : null
        );
        String comparisonMessage = similarSpecFinder.buildComparisonMessage(similarPassers.size(), jobType);

        // 4. 현재 신청 가능한 DB 활동 조회 (RAG 패턴 — Gemini에 선택지 제공)
        List<Activity> activeActivities = activityRepository.findRecommendableActivities(
                today,
                PageRequest.of(0, MAX_RECOMMENDABLE_ACTIVITIES)
        );
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJson(activeActivities);

        // 5. Gemini API 호출 (최대 2회 시도)
        String userSpecJson = promptDataBuilder.serializeSpecForRecommendation(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        String similarCasesStr = promptDataBuilder.buildSimilarCasesText(similarPassers);

        // 자격증 2층 인식(MatchScoreCalculator)에 쓸 전역 자격증 풀. 비교 대상 유사 합격자
        // (similarPassers, Top N)가 아니라 검증된 합격자 DB 전체에서 뽑는다 — Top N은 스펙을
        // 살짝만 고쳐도 바뀌는 값이라, 그걸 인식 기준으로 쓰면 같은 자격증이 요청마다
        // 인식됐다 안 됐다 흔들리게 된다.
        Set<String> globalCertPool = passerDataRepository.findAllVerifiedCertificationArrays().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());

        RecommendationResponse response = callGeminiWithRetry(
                userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson,
                userSpec, similarPassers, comparisonMessage, activeActivities,
                targetJob != null ? targetJob.getJobType() : "미설정", similarPassers.size(),
                globalCertPool
        );

        // 6. 결과 캐싱 (일일 제한 카운트 증가) — 별도 Bean에서 호출
        if (response.isAiRecommendation()) {
            recommendationCacheService.save(user, response);
        }

        return response;
    }

    private boolean hasUsableCachedActivities(RecommendationResponse response, LocalDate today) {
        return response != null
                && response.getActivities() != null
                && !response.getActivities().isEmpty()
                && response.getActivities().stream()
                .noneMatch(activity -> activity.getDeadline() != null
                        && activity.getDeadline().isBefore(today));
    }

    private boolean isSpecModifiedSince(UserSpec userSpec, TargetJob targetJob, java.time.LocalDateTime cacheCreatedAt) {
        if (cacheCreatedAt == null) return true;
        if (userSpec != null && userSpec.getUpdatedAt() != null && userSpec.getUpdatedAt().isAfter(cacheCreatedAt)) {
            return true;
        }
        if (targetJob != null && targetJob.getUpdatedAt() != null && targetJob.getUpdatedAt().isAfter(cacheCreatedAt)) {
            return true;
        }
        return false;
    }

    /**
     * Gemini API를 호출하고 JSON 파싱을 시도한다. 실패 시 1회 재시도 후 Fallback 반환.
     */
    private RecommendationResponse callGeminiWithRetry(
            String userSpecJson, String targetJobStr, String similarCasesStr, String availableActivitiesJson,
            UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage,
            List<Activity> activeActivities, String targetJobName, int similarPasserCount,
            Set<String> globalCertPool) {

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRecommendation(
                        userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson);
                if (rawJson != null && !rawJson.isBlank()) {
                    RecommendationResponse res = parseGeminiResponse(
                            rawJson, userSpec, similarPassers, comparisonMessage, activeActivities,
                            targetJobName, similarPasserCount, globalCertPool);
                    if (res != null) return res;
                }
            } catch (Exception e) {
                log.warn("Gemini 호출 또는 파싱 실패 (시도 {}/2): {}", attempt, e.getMessage());
            }
            if (attempt < 2) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }

        // 최종 실패 시 Fallback 반환
        log.error("Gemini 추천 생성 모두 실패. DB 활동 기반 기본 추천 반환.");
        return buildFallbackResponse(activeActivities, userSpec, similarPassers, comparisonMessage, targetJobName, similarPasserCount, globalCertPool);
    }

    /** Gemini 미사용/실패 시 DB 등록 활동 기반 기본 추천 반환 (방어 로직) */
    private RecommendationResponse buildFallbackResponse(List<Activity> activeActivities, UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage, String targetJobName, int similarPasserCount, Set<String> globalCertPool) {
        if (activeActivities == null || activeActivities.isEmpty()) {
            return null;
        }

        MatchScoreResult matchResult = matchScoreCalculator.calculate(userSpec, similarPassers, globalCertPool);
        boolean sampleComparisonData = containsSampleOrUnclassifiedData(similarPassers);

        List<ActivityRecommendation> recs = new ArrayList<>();
        int count = Math.min(3, activeActivities.size());
        for (int i = 0; i < count; i++) {
            Activity a = activeActivities.get(i);
            recs.add(ActivityRecommendation.builder()
                    .id(a.getId())
                    .type(a.getType())
                    .name(a.getName())
                    .reason(a.getDescription() != null && !a.getDescription().isBlank() 
                            ? "[AI 응답 지연 임시 추천] " + a.getDescription() 
                            : "[AI 응답 지연 임시 추천] 사용자의 목표 직무 및 학점 스펙 기반 DB 맞춤 추천 활동입니다.")
                    .deadline(a.getDeadline())
                    .build());
        }

        return RecommendationResponse.builder()
                .activities(recs)
                .matchScore(matchResult.getTotalScore())
                .compareRows(matchResult.getCompareRows())
                .unrecognizedCertifications(matchResult.getUnrecognizedCertifications())
                .targetJobName(targetJobName)
                .similarPasserCount(similarPasserCount)
                .comparisonMessage(comparisonMessage)
                .isAiRecommendation(false)
                .sampleComparisonData(sampleComparisonData)
                .scoreFormulaVersion(MatchScoreCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .build();
    }

    /**
     * Gemini 응답 JSON을 RecommendationResponse DTO로 변환한다.
     * 타입 안전한 GeminiRecommendationResult DTO로 파싱하고,
     * DB에 실재하는 활동만 포함하며, matchScore를 주입한다.
     */
    private RecommendationResponse parseGeminiResponse(
            String rawJson, UserSpec userSpec, List<PasserData> similarPassers,
            String comparisonMessage, List<Activity> activeActivities,
            String targetJobName, int similarPasserCount, Set<String> globalCertPool) throws Exception {

        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        // 타입 안전한 DTO로 파싱 (개선 #5)
        GeminiRecommendationResult geminiResult = objectMapper.readValue(rawJson, GeminiRecommendationResult.class);

        if (geminiResult.getActivities() == null || geminiResult.getActivities().isEmpty()) return null;

        MatchScoreResult matchResult = matchScoreCalculator.calculate(userSpec, similarPassers, globalCertPool);
        boolean sampleComparisonData = containsSampleOrUnclassifiedData(similarPassers);

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
                .matchScore(matchResult.getTotalScore())
                .compareRows(matchResult.getCompareRows())
                .unrecognizedCertifications(matchResult.getUnrecognizedCertifications())
                .targetJobName(targetJobName)
                .similarPasserCount(similarPasserCount)
                .comparisonMessage(comparisonMessage)
                .isAiRecommendation(true)
                .sampleComparisonData(sampleComparisonData)
                .scoreFormulaVersion(MatchScoreCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .build();
    }

    private boolean containsSampleOrUnclassifiedData(List<PasserData> similarPassers) {
        return similarPassers != null && similarPassers.stream()
                .anyMatch(passer -> passer.getDataOrigin() == null
                        || "DEMO".equalsIgnoreCase(passer.getDataOrigin())
                        || "UNKNOWN".equalsIgnoreCase(passer.getDataOrigin()));
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
