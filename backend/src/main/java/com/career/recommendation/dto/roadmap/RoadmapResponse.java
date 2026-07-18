package com.career.recommendation.dto.roadmap;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /api/v1/roadmaps 응답 DTO.
 * 학기/방학 단위로 구분된 6개월 커리어 타임라인을 반환합니다.
 */
@Getter
@Builder
public class RoadmapResponse {

    private List<TimelineStep> timeline;

    @Getter
    @Builder
    public static class TimelineStep {

        /** 예: "3학년 2학기", "7-8월 방학", "내년 상반기" */
        private String period;

        /** HIGH | MEDIUM | LOW */
        private String priority;

        private String activity;

        private String reason;
    }
}
