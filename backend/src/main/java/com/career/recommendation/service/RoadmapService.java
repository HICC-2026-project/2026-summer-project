package com.career.recommendation.service;

import com.career.recommendation.dto.gemini.GeminiRoadmapResult;
import com.career.recommendation.dto.gemini.GeminiRoadmapResult.GeminiTimelineStep;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.PromptDataBuilder;
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
 * BE-1 담당 — F-05 커리어 로드맵 비즈니스 로직.
 * 유저 학년을 기반으로 학기/방학 단위로 구분된 6개월 타임라인을 생성한다.
 *
 * RAG 패턴 적용 — DB 활동 목록을 Gemini 프롬프트에 주입하여
 * AI가 실제 존재하는 활동 중에서만 선택하도록 하고, 응답 ID를 DB와 검증하여 할루시네이션을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final ActivityRepository activityRepository;
    private final SimilarSpecFinder similarSpecFinder;
    private final RecommendationService recommendationService;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 현재 로그인한 유저의 6개월 커리어 로드맵을 반환한다.
     * F-03 맞춤 추천 활동 및 유사 합격자 데이터를 공유받아 일관성 있는 로드맵을 생성한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        String userSpecJson = promptDataBuilder.serializeSpecForRoadmap(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        // 1. 유사 합격자 케이스 조회 (F-03과 맥락 통일)
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        List<PasserData> similarPassers = similarSpecFinder.find(
                jobType,
                (userSpec != null) ? userSpec.getGpa() : null
        );
        String similarCasesStr = promptDataBuilder.buildSimilarCasesText(similarPassers);

        // 2. F-03 맞춤 추천 결과 조회 (추천 활동 우선 반영)
        String topRecommendedJson = "[]";
        try {
            RecommendationResponse recResponse = recommendationService.getRecommendations(authentication);
            if (recResponse != null && recResponse.getActivities() != null) {
                topRecommendedJson = objectMapper.writeValueAsString(recResponse.getActivities());
            }
        } catch (Exception e) {
            log.warn("F-03 추천 결과 연동 중 오류 (기본값 사용): {}", e.getMessage());
        }

        // 3. DB 활성 활동 목록 조회 (RAG 패턴)
        List<Activity> activeActivities = activityRepository.findByIsActiveTrue();
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJson(activeActivities);

        // 4. Gemini API 호출 (최대 2회 시도)
        return callGeminiWithRetry(userSpecJson, targetJobStr, grade,
                similarCasesStr, topRecommendedJson, availableActivitiesJson, activeActivities);
    }

    private RoadmapResponse callGeminiWithRetry(String userSpecJson, String targetJobStr, Integer grade,
                                                 String similarCasesStr, String topRecommendedJson,
                                                 String availableActivitiesJson, List<Activity> activeActivities) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRoadmap(
                        userSpecJson, targetJobStr, grade,
                        similarCasesStr, topRecommendedJson, availableActivitiesJson);
                if (rawJson.isBlank()) {
                    log.warn("Gemini 로드맵 응답 비어있음 (시도 {}회)", attempt);
                    continue;
                }
                RoadmapResponse parsed = parseGeminiResponse(rawJson, activeActivities);
                if (parsed != null) return parsed;
            } catch (Exception e) {
                log.warn("Gemini 로드맵 파싱 실패 (시도 {}회): {}", attempt, e.getMessage());
            }
        }
        log.info("Gemini 로드맵 미사용/실패 → DB 저장 활동 기반 로드맵 반환");
        return buildFallbackRoadmap(grade, activeActivities);
    }

    /**
     * Gemini 응답 JSON을 RoadmapResponse DTO로 변환한다.
     * 타입 안전한 GeminiRoadmapResult DTO로 파싱하고, DB와 대조하여 실재하는 활동만 포함한다.
     */
    private RoadmapResponse parseGeminiResponse(String rawJson,
                                                 List<Activity> activeActivities) throws Exception {
        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        // 타입 안전한 DTO로 파싱 (개선 #5)
        GeminiRoadmapResult geminiResult = objectMapper.readValue(rawJson, GeminiRoadmapResult.class);
        if (geminiResult.getTimeline() == null || geminiResult.getTimeline().isEmpty()) return null;

        List<TimelineStep> steps = new ArrayList<>();
        for (GeminiTimelineStep t : geminiResult.getTimeline()) {
            String period = t.getPeriod() != null ? t.getPeriod() : "";

            // Gemini가 반환한 activityIds에서 DB에 실재하는 활동만 매칭
            List<MatchedActivity> matched = new ArrayList<>();
            if (t.getActivityIds() != null) {
                for (String idStr : t.getActivityIds()) {
                    try {
                        UUID activityId = UUID.fromString(idStr);
                        if (activityMap.containsKey(activityId)) {
                            matched.add(toMatchedActivity(activityMap.get(activityId)));
                        } else {
                            log.warn("Gemini 로드맵이 DB에 없는 활동 ID를 반환함 (무시): {}", idStr);
                        }
                    } catch (Exception ignored) {
                        log.warn("Gemini 로드맵이 잘못된 형식의 ID를 반환함 (무시): {}", idStr);
                    }
                }
            }

            String rawActivity = t.getActivity();
            String activityText;
            if (rawActivity != null && !rawActivity.isBlank() && !"null".equalsIgnoreCase(rawActivity)) {
                activityText = rawActivity;
            } else {
                activityText = matched.stream().map(MatchedActivity::getName).reduce((a, b) -> a + ", " + b).orElse("");
            }

            steps.add(TimelineStep.builder()
                    .period(period)
                    .priority(t.getPriority() != null ? t.getPriority() : "MEDIUM")
                    .activity(activityText)
                    .reason(t.getReason() != null ? t.getReason() : "")
                    .matchedActivities(matched)
                    .build());
        }

        if (steps.isEmpty()) return null;

        return RoadmapResponse.builder().timeline(steps).build();
    }

    private MatchedActivity toMatchedActivity(Activity activity) {
        return MatchedActivity.builder()
                .activityId(activity.getId())
                .name(activity.getName())
                .type(activity.getType())
                .organization(activity.getOrganization())
                .deadline(activity.getDeadline())
                .url(activity.getUrl())
                .build();
    }

    /** Gemini 미사용/실패 시 DB 등록 활동 기반 기본 로드맵 반환 */
    private RoadmapResponse buildFallbackRoadmap(Integer grade, List<Activity> activeActivities) {
        String semester1 = (grade != null) ? grade + "학년 1학기 (9~11월)" : "1~2개월 차";
        String semester2 = (grade != null) ? grade + "학년 겨울방학 (12~2월)" : "3~4개월 차";
        String semester3 = (grade != null) ? (grade < 4 ? (grade + 1) + "학년 1학기" : "4학년 2학기") : "5~6개월 차";

        List<MatchedActivity> step1Matched = new ArrayList<>();
        List<MatchedActivity> step2Matched = new ArrayList<>();
        List<MatchedActivity> step3Matched = new ArrayList<>();

        if (activeActivities != null) {
            for (int i = 0; i < activeActivities.size(); i++) {
                Activity a = activeActivities.get(i);
                if (i < 3) step1Matched.add(toMatchedActivity(a));
                else if (i < 6) step2Matched.add(toMatchedActivity(a));
                else if (i < 9) step3Matched.add(toMatchedActivity(a));
            }
        }

        return RoadmapResponse.builder()
                .timeline(List.of(
                        TimelineStep.builder()
                                .period(semester1)
                                .priority("HIGH")
                                .activity("핵심 SW 교육 및 인턴십 지원")
                                .reason("서류 가점 및 기초 실무 역량을 다지는 핵심 시기입니다.")
                                .matchedActivities(step1Matched)
                                .build(),
                        TimelineStep.builder()
                                .period(semester2)
                                .priority("HIGH")
                                .activity("부트캠프 및 프로젝트 몰입")
                                .reason("방학 기간을 활용하여 포트폴리오를 대폭 강화합니다.")
                                .matchedActivities(step2Matched)
                                .build(),
                        TimelineStep.builder()
                                .period(semester3)
                                .priority("MEDIUM")
                                .activity("오픈소스 기여 및 해커톤 공모전 참가")
                                .reason("실무 협업 역량을 입증하고 채용 우대 혜택을 획득합니다.")
                                .matchedActivities(step3Matched)
                                .build()
                ))
                .build();
    }
}
