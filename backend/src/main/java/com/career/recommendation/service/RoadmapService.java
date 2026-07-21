package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — F-05 커리어 로드맵 비즈니스 로직.
 * 유저 학년을 기반으로 학기/방학 단위로 구분된 6개월 타임라인을 생성한다.
 *
 * [개선] RAG 패턴 적용 — AI가 타임라인을 생성한 뒤, 각 시기에 해당하는
 * 실제 DB 대외활동(Activity)을 매칭하여 할루시네이션을 방지한다.
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
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper;

    /** period 텍스트에서 월 숫자를 추출하는 패턴 (예: "7월", "9~11월", "12월~2월") */
    private static final Pattern MONTH_PATTERN = Pattern.compile("(\\d{1,2})");

    /**
     * 현재 로그인한 유저의 6개월 커리어 로드맵을 반환한다.
     * AI가 생성한 타임라인 각 단계에 해당 시기에 지원 가능한 실제 DB 활동을 매칭한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        String userSpecJson = serializeSpec(userSpec);
        String targetJobStr = buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        // 1단계: AI가 로드맵 타임라인 생성
        RoadmapResponse roadmap = callClaudeWithRetry(userSpecJson, targetJobStr, grade);

        // 2단계: 각 타임라인 단계에 시기가 맞는 실제 DB 활동 매칭
        return enrichWithMatchedActivities(roadmap);
    }

    /**
     * AI가 생성한 로드맵의 각 타임라인 단계에 대해,
     * 해당 시기(startMonth~endMonth)에 마감되는 실제 DB 활동을 조회하여 매칭한다.
     */
    private RoadmapResponse enrichWithMatchedActivities(RoadmapResponse roadmap) {
        if (roadmap == null || roadmap.getTimeline() == null) return roadmap;

        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();

        List<TimelineStep> enrichedSteps = new ArrayList<>();

        for (TimelineStep step : roadmap.getTimeline()) {
            // period 텍스트에서 시작/종료 월 파싱
            int[] monthRange = parseMonthRange(step.getPeriod());
            int startMonth = (step.getStartMonth() != null) ? step.getStartMonth() : monthRange[0];
            int endMonth   = (step.getEndMonth() != null) ? step.getEndMonth() : monthRange[1];

            // 해당 시기에 마감되는 활성 활동 조회
            List<MatchedActivity> matched = findActivitiesForPeriod(startMonth, endMonth, currentYear, now);

            enrichedSteps.add(TimelineStep.builder()
                    .period(step.getPeriod())
                    .startMonth(startMonth)
                    .endMonth(endMonth)
                    .priority(step.getPriority())
                    .activity(step.getActivity())
                    .reason(step.getReason())
                    .matchedActivities(matched)
                    .build());
        }

        return RoadmapResponse.builder().timeline(enrichedSteps).build();
    }

    /**
     * 지정된 월 범위에 마감일이 포함되는 활성 대외활동을 DB에서 조회한다.
     * 이미 마감된(deadline < 오늘) 활동은 제외한다.
     * 시기당 최대 3개까지 반환한다.
     */
    private List<MatchedActivity> findActivitiesForPeriod(int startMonth, int endMonth, int year, LocalDate now) {
        // 시기 범위에 해당하는 날짜 구간 계산
        LocalDate rangeStart;
        LocalDate rangeEnd;

        if (startMonth <= endMonth) {
            // 같은 해 (예: 7~8월, 9~11월)
            rangeStart = LocalDate.of(year, startMonth, 1);
            rangeEnd   = LocalDate.of(year, endMonth, 1).plusMonths(1).minusDays(1);
        } else {
            // 연도 넘김 (예: 12월~2월 → 올해 12월 ~ 내년 2월)
            rangeStart = LocalDate.of(year, startMonth, 1);
            rangeEnd   = LocalDate.of(year + 1, endMonth, 1).plusMonths(1).minusDays(1);
        }

        // 이미 지난 마감일은 제외: 조회 시작일은 오늘과 rangeStart 중 늦은 쪽
        LocalDate effectiveStart = now.isAfter(rangeStart) ? now : rangeStart;

        List<Activity> activities = activityRepository
                .findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc(effectiveStart, rangeEnd);

        // 시기당 최대 3개까지만 매칭
        return activities.stream()
                .limit(3)
                .map(this::toMatchedActivity)
                .toList();
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

    /**
     * period 텍스트에서 월 범위를 추출한다.
     * 예: "3학년 2학기 (9~11월)" → [9, 11]
     *     "7월" → [7, 7]
     *     "겨울방학 (12월~2월)" → [12, 2]
     * 파싱 실패 시 현재 월 기준 기본값 반환.
     */
    int[] parseMonthRange(String period) {
        if (period == null || period.isBlank()) {
            int current = LocalDate.now().getMonthValue();
            return new int[]{current, current};
        }

        Matcher matcher = MONTH_PATTERN.matcher(period);
        List<Integer> months = new ArrayList<>();
        while (matcher.find()) {
            int m = Integer.parseInt(matcher.group(1));
            if (m >= 1 && m <= 12) {
                months.add(m);
            }
        }

        if (months.isEmpty()) {
            // "상반기", "하반기" 등 월 정보가 없는 경우 키워드 기반 추정
            if (period.contains("상반기") || period.contains("1학기")) return new int[]{3, 6};
            if (period.contains("하반기") || period.contains("2학기")) return new int[]{9, 12};
            if (period.contains("여름") || period.contains("여름방학")) return new int[]{7, 8};
            if (period.contains("겨울") || period.contains("겨울방학")) return new int[]{12, 2};
            int current = LocalDate.now().getMonthValue();
            return new int[]{current, current};
        }

        if (months.size() == 1) {
            return new int[]{months.get(0), months.get(0)};
        }

        // 마지막 두 숫자를 시작/종료 월로 사용 (학년 숫자 등 앞부분 제외)
        return new int[]{months.get(months.size() - 2), months.get(months.size() - 1)};
    }

    private RoadmapResponse callClaudeWithRetry(String userSpecJson, String targetJobStr, Integer grade) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = claudeService.generateRoadmap(userSpecJson, targetJobStr, grade);
                if (rawJson.isBlank()) {
                    log.warn("Claude 로드맵 응답 비어있음 (시도 {}회)", attempt);
                    continue;
                }
                RoadmapResponse parsed = parseClaudeResponse(rawJson);
                if (parsed != null) return parsed;
            } catch (Exception e) {
                log.warn("Claude 로드맵 파싱 실패 (시도 {}회): {}", attempt, e.getMessage());
            }
        }
        log.error("Claude 로드맵 2회 연속 실패 → 기본 로드맵 반환");
        return buildFallbackRoadmap(grade);
    }

    @SuppressWarnings("unchecked")
    private RoadmapResponse parseClaudeResponse(String rawJson) throws Exception {
        Map<String, Object> root = objectMapper.readValue(rawJson, new TypeReference<>() {});
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) root.get("timeline");
        if (timeline == null || timeline.isEmpty()) return null;

        List<TimelineStep> steps = new ArrayList<>();
        for (Map<String, Object> t : timeline) {
            String period = String.valueOf(t.getOrDefault("period", ""));
            int[] monthRange = parseMonthRange(period);

            steps.add(TimelineStep.builder()
                    .period(period)
                    .startMonth(monthRange[0])
                    .endMonth(monthRange[1])
                    .priority(String.valueOf(t.getOrDefault("priority", "MEDIUM")))
                    .activity(String.valueOf(t.getOrDefault("activity", "")))
                    .reason(String.valueOf(t.getOrDefault("reason", "")))
                    .build());
        }
        return RoadmapResponse.builder().timeline(steps).build();
    }

    /** Claude 완전 실패 시 기본 로드맵 반환 */
    private RoadmapResponse buildFallbackRoadmap(Integer grade) {
        String semester1 = (grade != null) ? grade + "학년 1학기" : "상반기";
        String semester2 = (grade != null) ? grade + "학년 여름방학" : "여름";
        String semester3 = (grade != null) ? grade + "학년 2학기" : "하반기";

        return RoadmapResponse.builder()
                .timeline(List.of(
                        TimelineStep.builder()
                                .period(semester1)
                                .startMonth(3).endMonth(6)
                                .priority("HIGH")
                                .activity("자격증 취득 (정보처리기사 등)")
                                .reason("기본 역량 인증으로 서류 통과율을 높일 수 있습니다.")
                                .build(),
                        TimelineStep.builder()
                                .period(semester2)
                                .startMonth(7).endMonth(8)
                                .priority("HIGH")
                                .activity("스타트업 인턴십 또는 부트캠프 참여")
                                .reason("방학 기간 동안 실무 경험을 쌓는 것이 효과적입니다.")
                                .build(),
                        TimelineStep.builder()
                                .period(semester3)
                                .startMonth(9).endMonth(12)
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
}
