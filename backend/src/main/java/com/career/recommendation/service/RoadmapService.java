package com.career.recommendation.service;

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
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    private final ObjectMapper objectMapper;

    /**
     * 현재 로그인한 유저의 6개월 커리어 로드맵을 반환한다.
     * F-03 맞춤 추천 활동 및 유사 합격자 데이터를 공유받아 일관성 있는 로드맵을 생성한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        String userSpecJson = serializeSpec(userSpec);
        String targetJobStr = buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        // 1. 유사 합격자 케이스 조회 (F-03과 맥락 통일)
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        List<PasserData> similarPassers = similarSpecFinder.find(
                jobType,
                (userSpec != null) ? userSpec.getGpa() : null
        );
        String similarCasesStr = buildSimilarCasesText(similarPassers);

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
        String availableActivitiesJson = buildAvailableActivitiesJson(activeActivities);

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
        log.error("Gemini 로드맵 2회 연속 실패 → 기본 로드맵 반환");
        return buildFallbackRoadmap(grade);
    }

    /**
     * Gemini 응답 JSON을 RoadmapResponse DTO로 변환한다.
     * Gemini가 반환한 activityIds를 DB와 대조하여 실재하는 활동만 포함한다.
     */
    @SuppressWarnings("unchecked")
    private RoadmapResponse parseGeminiResponse(String rawJson,
                                                 List<Activity> activeActivities) throws Exception {
        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        Map<String, Object> root = objectMapper.readValue(rawJson, new TypeReference<>() {});
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) root.get("timeline");
        if (timeline == null || timeline.isEmpty()) return null;

        List<TimelineStep> steps = new ArrayList<>();
        for (Map<String, Object> t : timeline) {
            String period = String.valueOf(t.getOrDefault("period", ""));

            // Gemini가 반환한 activityIds에서 DB에 실재하는 활동만 매칭
            List<MatchedActivity> matched = new ArrayList<>();
            Object idsObj = t.get("activityIds");
            if (idsObj instanceof List<?> idList) {
                for (Object idObj : idList) {
                    try {
                        UUID activityId = UUID.fromString(String.valueOf(idObj));
                        if (activityMap.containsKey(activityId)) {
                            matched.add(toMatchedActivity(activityMap.get(activityId)));
                        } else {
                            log.warn("Gemini 로드맵이 DB에 없는 활동 ID를 반환함 (무시): {}", idObj);
                        }
                    } catch (Exception ignored) {
                        log.warn("Gemini 로드맵이 잘못된 형식의 ID를 반환함 (무시): {}", idObj);
                    }
                }
            }

            String rawActivity = String.valueOf(t.getOrDefault("activity", ""));
            String activityText;
            if (!rawActivity.isBlank() && !"null".equalsIgnoreCase(rawActivity)) {
                activityText = rawActivity;
            } else {
                activityText = matched.stream().map(MatchedActivity::getName).reduce((a, b) -> a + ", " + b).orElse("");
            }

            steps.add(TimelineStep.builder()
                    .period(period)
                    .priority(String.valueOf(t.getOrDefault("priority", "MEDIUM")))
                    .activity(activityText)
                    .reason(String.valueOf(t.getOrDefault("reason", "")))
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

    /** Gemini 완전 실패 시 기본 로드맵 반환 */
    private RoadmapResponse buildFallbackRoadmap(Integer grade) {
        String semester1 = (grade != null) ? grade + "학년 1학기" : "상반기";
        String semester2 = (grade != null) ? grade + "학년 여름방학" : "여름";
        String semester3 = (grade != null) ? grade + "학년 2학기" : "하반기";

        return RoadmapResponse.builder()
                .timeline(List.of(
                        TimelineStep.builder()
                                .period(semester1)
                                .priority("HIGH")
                                .activity("자격증 취득 (정보처리기사 등)")
                                .reason("기본 역량 인증으로 서류 통과율을 높일 수 있습니다.")
                                .build(),
                        TimelineStep.builder()
                                .period(semester2)
                                .priority("HIGH")
                                .activity("스타트업 인턴십 또는 부트캠프 참여")
                                .reason("방학 기간 동안 실무 경험을 쌓는 것이 효과적입니다.")
                                .build(),
                        TimelineStep.builder()
                                .period(semester3)
                                .priority("MEDIUM")
                                .activity("SW 공모전 1개 참가")
                                .reason("수상 실적이 포트폴리오를 강화합니다.")
                                .build()
                ))
                .build();
    }

    private String buildTargetJobString(TargetJob targetJob) {
        if (targetJob == null) return "미설정";
        return targetJob.getJobType() + " / " + targetJob.getCompanySize() + " / " + targetJob.getIndustry();
    }

    private String serializeSpec(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력",
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{}
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

    /**
     * DB 활성 활동 목록을 Gemini 프롬프트용 JSON 문자열로 변환한다.
     */
    private String buildAvailableActivitiesJson(List<Activity> activities) {
        if (activities.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Activity a : activities) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId().toString());
                item.put("type", a.getType());
                item.put("name", a.getName());
                item.put("organization", a.getOrganization());
                if (a.getDescription() != null) {
                    item.put("description", a.getDescription());
                }
                if (a.getDeadline() != null) {
                    item.put("deadline", a.getDeadline().toString());
                }
                if (a.getTags() != null) {
                    item.put("tags", a.getTags());
                }
                list.add(item);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("활동 목록 JSON 변환 실패: {}", e.getMessage());
            return "[]";
        }
    }
}
