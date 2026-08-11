package com.career.recommendation.dto.roadmap;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/roadmaps 응답 DTO.
 * 학기/방학 단위로 구분된 6개월 커리어 타임라인을 반환합니다.
 * 각 타임라인 단계에는 Gemini가 선택한 실제 DB 활동 목록이 포함됩니다.
 *
 * ⚠️ @Jacksonized가 필수다(RecommendationResponse와 같은 이유). 이 애노테이션 없이도
 * 지금까지는 "우연히" 역직렬화가 됐다 — Spring Boot 부모 POM의 -parameters 컴파일 옵션과
 * jackson-module-parameter-names가 classpath에 있어 Lombok의 all-args 생성자를 Jackson이
 * 암묵적으로 찾아 썼기 때문이다. 하지만 그 경로는 생성자 파라미터명을 그대로 JSON 키로
 * 기대하는데, boolean 필드가 "is"로 시작하면(isAiRoadmap) Jackson의 getter 기반 직렬화가
 * "is"를 떼고 "aiRoadmap"으로 쓴다 — 그러면 파라미터명("isAiRoadmap")과 직렬화 키("aiRoadmap")가
 * 어긋나 캐시(RoadmapCache.resultJson)를 역직렬화할 때 이 필드만 조용히 기본값(false)으로
 * 떨어진다. Gemini가 정상 생성한 로드맵도 캐시 히트만 되면 "일반 로드맵(폴백)"으로 잘못
 * 보고되는 버그였다 — RecommendationResponse.aiRecommendation과 정확히 같은 근본 원인의
 * 자매 버그다. 필드명을 aiRoadmap으로 바꿔 getter(isAiRoadmap(), API 응답 필드명)는 그대로
 * 유지하면서 빌더 메서드명(aiRoadmap)을 직렬화 키와 일치시켰다.
 */
@Getter
@Builder
@Jacksonized
public class RoadmapResponse {

    private List<TimelineStep> timeline;

    @Builder.Default
    private boolean aiRoadmap = true;

    @Getter
    @Builder
    @Jacksonized
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
    @Jacksonized
    public static class MatchedActivity {

        private UUID activityId;

        /** 예: "삼성 청년 SW 아카데미(SSAFY) 12기" */
        private String name;

        /** INTERNSHIP | EXTERNAL | COMPETITION | EDUCATION */
        private String type;

        /** 주최 기관 */
        private String organization;

        /** 신청 마감일 */
        private LocalDate deadline;

        /** 공식 지원 페이지 URL */
        private String url;
    }
}
