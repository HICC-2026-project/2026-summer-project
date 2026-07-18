package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BE-1 담당 — F-05 커리어 로드맵 비즈니스 로직.
 * 유저 학년을 기반으로 학기/방학 단위로 구분된 6개월 타임라인을 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper;

    /**
     * 현재 로그인한 유저의 6개월 커리어 로드맵을 반환한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        String userSpecJson = serializeSpec(userSpec);
        String targetJobStr = buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        return callClaudeWithRetry(userSpecJson, targetJobStr, grade);
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
            steps.add(TimelineStep.builder()
                    .period(String.valueOf(t.getOrDefault("period", "")))
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
}
