package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.MatchScoreCalculator;
import com.career.recommendation.util.RecommendationFallbackData;
import com.career.recommendation.util.SimilarSpecFinder;
import com.career.recommendation.service.RecommendationCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BE-1 담당 — F-03 활동 추천 비즈니스 로직.
 *
 * 캐시 전략: 유저당 1건, 24시간 만료.
 * Claude 실패 처리: 1회 재시도 → 2회 연속 실패 시 Fallback 데이터 반환 + isAiRecommendation=false.
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
    private final SimilarSpecFinder similarSpecFinder;
    private final MatchScoreCalculator matchScoreCalculator;
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper;

    private static final int CACHE_HOURS = 24;

    /**
     * 현재 로그인한 유저의 맞춤 추천 활동 목록을 반환한다.
     * 유효한 캐시가 있으면 DB에서 즉시 반환한다.
     */
    @Transactional
    public RecommendationResponse getRecommendations(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        // 1. 유효 캐시 확인
        Recommendation cached = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
        if (cached != null && cached.isValid()) {
            return deserialize(cached.getResultJson());
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

        // 4. Claude API 호출 (최대 2회 시도)
        String userSpecJson = serializeSpec(userSpec);
        String targetJobStr = (targetJob != null)
                ? targetJob.getJobType() + " / " + targetJob.getCompanySize() + " / " + targetJob.getIndustry()
                : "미설정";
        String similarCasesStr = buildSimilarCasesText(similarPassers);

        RecommendationResponse response = callClaudeWithRetry(
                userSpecJson, targetJobStr, similarCasesStr,
                userSpec, similarPassers, comparisonMessage
        );

        // 5. 결과 캐싱 (24시간) — 별도 Bean에서 호출해야 @Transactional 프록시가 정상 동작함
        recommendationCacheService.save(user, response, CACHE_HOURS);

        return response;
    }

    /**
     * Claude API를 호출하고 JSON 파싱을 시도한다. 실패 시 1회 재시도 후 Fallback 반환.
     */
    private RecommendationResponse callClaudeWithRetry(
            String userSpecJson, String targetJobStr, String similarCasesStr,
            UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage) {

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = claudeService.generateRecommendation(userSpecJson, targetJobStr, similarCasesStr);
                if (rawJson.isBlank()) {
                    log.warn("Claude 추천 응답 비어있음 (시도 {}회)", attempt);
                    continue;
                }

                RecommendationResponse parsed = parseClaudeResponse(rawJson, userSpec, similarPassers, comparisonMessage);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("Claude 추천 파싱 실패 (시도 {}회): {}", attempt, e.getMessage());
            }
        }

        log.error("Claude 추천 2회 연속 실패 → Fallback 반환");
        return RecommendationFallbackData.get();
    }

    /**
     * Claude 응답 JSON을 RecommendationResponse DTO로 변환하고 matchScore를 주입한다.
     */
    @SuppressWarnings("unchecked")
    private RecommendationResponse parseClaudeResponse(
            String rawJson, UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage) throws Exception {

        Map<String, Object> root = objectMapper.readValue(rawJson, new TypeReference<>() {});
        List<Map<String, Object>> activities = (List<Map<String, Object>>) root.get("activities");

        if (activities == null || activities.isEmpty()) return null;

        int overallMatchScore = matchScoreCalculator.calculate(userSpec, similarPassers);

        List<ActivityRecommendation> result = new ArrayList<>();
        for (Map<String, Object> a : activities) {
            String deadlineStr = (String) a.get("deadline");
            LocalDate deadline = null;
            if (deadlineStr != null && !deadlineStr.isBlank()) {
                try { deadline = LocalDate.parse(deadlineStr); } catch (Exception ignored) {}
            }

            // id가 없거나 숫자로 들어오는 경우 null 처리 (UUID 타입으로 통일)
            UUID activityId = null;
            Object idObj = a.get("id");
            if (idObj instanceof String s) {
                try { activityId = UUID.fromString(s); } catch (Exception ignored) {}
            }

            result.add(ActivityRecommendation.builder()
                    .id(activityId)
                    .type(String.valueOf(a.getOrDefault("type", "")))
                    .name(String.valueOf(a.getOrDefault("name", "")))
                    .reason(String.valueOf(a.getOrDefault("reason", "")))
                    .deadline(deadline)
                    .build());
        }

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

    private String serializeSpec(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "gpaMax", userSpec.getGpaMax() != null ? userSpec.getGpaMax() : 4.5,
                    "languageScores", userSpec.getLanguageScores() != null ? userSpec.getLanguageScores() : List.of(),
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{},
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력"
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildSimilarCasesText(List<PasserData> passerList) {
        if (passerList.isEmpty()) return "유사 합격자 데이터 없음";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passerList.size(); i++) {
            PasserData p = passerList.get(i);
            sb.append(String.format("합격자%d: 학점=%s, 경험수=%d, 자격증=%s\n",
                    i + 1,
                    p.getGpa() != null ? p.getGpa() : "미상",
                    p.getExperienceCount() != null ? p.getExperienceCount() : 0,
                    p.getCertifications() != null ? String.join(", ", p.getCertifications()) : "없음"
            ));
        }
        return sb.toString();
    }
}
