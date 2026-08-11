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
import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BE-1 담당 — F-03 활동 추천 비즈니스 로직.
 *
 * 캐시 전략: 유저당 1건, 스펙 변경 시 즉시 갱신 (하루 최대 3회).
 * Gemini 실패 처리: 1회 재시도(2초 백오프) → 2회 연속 실패 시 Fallback 데이터 반환 + isAiRecommendation=false.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCacheService recommendationCacheService;
    private final ActivityRepository activityRepository;
    private final GlobalCertPoolService globalCertPoolService;
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
    /**
     * 트랜잭션 없이 전체 흐름을 오케스트레이션한다.
     * DB 조회는 각 리포지토리 메서드의 기본 트랜잭션에 의존하고,
     * Gemini API 호출은 트랜잭션 바깥에서 수행하여 DB 커넥션을 점유하지 않는다.
     */
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

            // ⚠️ isLegacyCache는 예전엔 이 하루 제한 체크를 건너뛰고 무조건 needsNewAiCall=true였다
            // ("한 번만 재생성하니 괜찮다"는 의도). 그런데 폴백 응답은 저장되지 않으므로
            // (isAiRecommendation()==false일 때 recommendationCacheService.save()를 안 부름),
            // Gemini가 장애 중이거나 키 미설정이면 "한 번"이 아니라 그 유저가 요청할 때마다
            // 매번 legacy 판정 → Gemini 2회 호출(2초 sleep 포함, 최대 60초 타임아웃) → 폴백 →
            // 캐시 미저장 → 다음 요청도 다시 legacy, 이 반복이 스스로 끝나지 않았다.
            // scoreFormulaVersion을 올려 배포하는 순간 전 유저 캐시가 동시에 legacy가 되는
            // 시나리오와 겹치면 실제로 밟을 수 있는 경로다. legacy도 같은 하루 3회 게이트를
            // 통과하게 해서, 하루 제한에 도달하면 legacy든 아니든 옛 캐시를 그대로 반환한다.
            if (isLegacyCache || !hasUsableActivities || isSpecChanged) {
                // 이 체크가 없으면 Gemini가 장애로 폴백만 반환할 때(캐시 미저장) 매 요청마다
                // API를 호출하게 된다(RoadmapService와 동일한 안전장치).
                if (cached.getLastUpdatedDate() == null || !today.equals(cached.getLastUpdatedDate())) {
                    needsNewAiCall = true;
                } else if (cached.getDailyUpdateCount() < 3) {
                    needsNewAiCall = true;
                }
            }
        }

        // ⚠️ 알려진 한계(다음 세션에서 스키마 변경으로 완전히 고쳐야 함): 이 하루 3회 게이트
        // 전체가 "성공 횟수"만 센다 — dailyUpdateCount는 오직 저장 성공(recommendationCacheService
        // .save()가 실제로 실행됐을 때, 즉 response.isAiRecommendation()==true일 때)에만
        // 증가한다. Gemini가 계속 실패하면 폴백만 반복되고 카운트는 0에 머무르므로, 위
        // 게이트는 "하루 3회"를 절대 못 보고 매 요청마다 무조건 통과시킨다 — 주석이
        // "안전장치"라고 부르는 게 실패 반복 상황에서는 실제로 발동하지 않는다는 뜻이다.
        // 게다가 cachedResponse == null(캐시 JSON 자체가 파싱 불가)이면 아래 118행의 조기
        // 반환 조건(cachedResponse != null)을 만족 못해, needsNewAiCall이 false로 막혀도
        // 그대로 Gemini 재시도 경로로 흘러내려간다 — 이 경우엔 하루 제한이 완전히 우회된다.
        // 실패 시도까지 세는 별도 카운터(예: lastAttemptDate + dailyAttemptCount 컬럼)를
        // 추가해 성공 여부와 무관하게 증가시켜야 이 게이트가 이름값대로 동작한다.
        // Opus 5(높음) 검토 11라운드가 실제 부하로 재현: legacy 캐시 + 하루 카운트 0(=장애
        // 상황의 실제 상태)에서 5회 요청이 Gemini를 10회 호출하는 것을 확인함.

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
        String comparisonMessage = similarSpecFinder.buildComparisonMessage(similarPassers, jobType);

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

        // 자격증 2층 인식(MatchScoreCalculator)에 쓸 전역 자격증 풀.
        // GlobalCertPoolService가 Caffeine 캐시로 1시간 유지하므로 매 요청마다 DB 전체 스캔하지 않는다.
        Set<String> globalCertPool = globalCertPoolService.getGlobalCertPool();

        // 자격증 가중치를 유도할 목표 직무 합격자 전원의 자격증(직무별로 캐시됨).
        // 직무가 없으면 빈 목록을 받아 MatchScoreCalculator가 기존 큐레이션 표로 폴백하게 한다.
        List<String[]> jobPasserCertRows = globalCertPoolService.getJobPasserCertRows(jobType);

        RecommendationResponse response = callGeminiWithRetry(
                userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson,
                userSpec, similarPassers, comparisonMessage, activeActivities,
                targetJob != null ? targetJob.getJobType() : "미설정", similarPassers.size(),
                globalCertPool, jobPasserCertRows
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
            Set<String> globalCertPool, List<String[]> jobPasserCertRows) {

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRecommendation(
                        userSpecJson, targetJobStr, similarCasesStr, availableActivitiesJson);
                if (rawJson != null && !rawJson.isBlank()) {
                    RecommendationResponse res = parseGeminiResponse(
                            rawJson, userSpec, similarPassers, comparisonMessage, activeActivities,
                            targetJobName, similarPasserCount, globalCertPool, jobPasserCertRows);
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
        return buildFallbackResponse(activeActivities, userSpec, similarPassers, comparisonMessage, targetJobName, similarPasserCount, globalCertPool, jobPasserCertRows);
    }

    /**
     * Gemini 미사용/실패 시 DB 등록 활동 기반 기본 추천 반환 (방어 로직).
     *
     * ⚠️ 이 메서드는 절대 null을 반환하지 않는다. 호출부(getRecommendations)가 반환값에
     * 곧바로 isAiRecommendation()을 호출하므로, null을 주면 추천 API 전체가 NPE로 500이 된다.
     * 추천 가능한 활동이 0건인 상황(전 활동 마감·비활성화, 크롤링 중단 등)은 장애가 아니라
     * 정상적으로 발생할 수 있는 상태이므로, 활동 목록만 비운 응답을 만든다.
     * 점수·비교표·미인식 자격증 고지는 활동 유무와 무관하게 계산되므로 그대로 내려보낸다
     * (화면은 활동 0건을 "아직 추천할 활동이 없어요"로 이미 처리한다).
     */
    private RecommendationResponse buildFallbackResponse(List<Activity> activeActivities, UserSpec userSpec, List<PasserData> similarPassers, String comparisonMessage, String targetJobName, int similarPasserCount, Set<String> globalCertPool, List<String[]> jobPasserCertRows) {
        List<Activity> safeActivities = (activeActivities != null) ? activeActivities : List.of();

        MatchScoreResult matchResult = matchScoreCalculator.calculate(userSpec, similarPassers, globalCertPool, jobPasserCertRows);
        boolean sampleComparisonData = containsSampleOrUnclassifiedData(similarPassers);

        List<ActivityRecommendation> recs = new ArrayList<>();
        int count = Math.min(3, safeActivities.size());
        for (int i = 0; i < count; i++) {
            Activity a = safeActivities.get(i);
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
                .aiRecommendation(false)
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
            String targetJobName, int similarPasserCount, Set<String> globalCertPool, List<String[]> jobPasserCertRows) throws Exception {

        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        // 타입 안전한 DTO로 파싱 (개선 #5)
        GeminiRecommendationResult geminiResult = objectMapper.readValue(rawJson, GeminiRecommendationResult.class);

        if (geminiResult.getActivities() == null || geminiResult.getActivities().isEmpty()) return null;

        MatchScoreResult matchResult = matchScoreCalculator.calculate(userSpec, similarPassers, globalCertPool, jobPasserCertRows);
        boolean sampleComparisonData = containsSampleOrUnclassifiedData(similarPassers);

        List<ActivityRecommendation> result = new ArrayList<>();
        for (GeminiActivity a : geminiResult.getActivities()) {
            // Gemini가 배열 원소로 null을 섞어 보내면(스키마 이탈) a.getId()에서 NPE가 나
            // 이 시도 전체가 버려진다 — 정상 원소가 섞여 있어도 배열 전체를 버리고 재시도
            // (2초 sleep + 최대 60초 Gemini 재호출)를 낭비하게 되므로, null 원소만 건너뛴다.
            if (a == null) continue;

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
                .aiRecommendation(true)
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
