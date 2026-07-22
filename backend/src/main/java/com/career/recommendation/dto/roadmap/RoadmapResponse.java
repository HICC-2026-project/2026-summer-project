package com.career.recommendation.dto.roadmap;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/roadmaps 응답 DTO.
 * 학기/방학 단위로 구분된 6개월 커리어 타임라인을 반환합니다.
 * 각 타임라인 단계에는 Gemini가 선택한 실제 DB 활동 목록이 포함됩니다.
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

        /** AI가 해당 시기에 추천하는 활동 요약 (매칭된 활동명 조합) */
        private String activity;

        /** AI가 생성한 추천 이유 */
        private String reason;

        /** 해당 시기에 추천된 실제 DB 대외활동 목록 (Gemini가 선택 → DB 검증) */
        @Builder.Default
        private List<MatchedActivity> matchedActivities = List.of();
    }

    /**
     * DB에 실제 존재하는 대외활동 · 공모전 · 인턴 정보.
     * 이름, 마감일, 지원 링크 등 모든 필드가 DB 원본 데이터이므로 정확성이 보장됩니다.
     */
    @Getter
    @Builder
    public static class MatchedActivity {

        private UUID activityId;

        /** 예: "삼성 청년 SW 아카데미(SSAFY) 12기" */
        private String name;

        /** INTERNSHIP | EXTERNAL | COMPETITION */
        private String type;

        /** 주최 기관 */
        private String organization;

        /** 신청 마감일 */
        private LocalDate deadline;

        /** 공식 지원 페이지 URL */
        private String url;
    }
}
