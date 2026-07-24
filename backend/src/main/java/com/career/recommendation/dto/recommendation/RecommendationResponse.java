package com.career.recommendation.dto.recommendation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/recommendations 응답 DTO.
 *
 * isAiRecommendation: Claude 정상 응답이면 true, Fallback이면 false → 프론트에서 "일반 추천" 배지 표시.
 * comparisonMessage: 유사 합격자 수에 따른 요약 메시지.
 */
@Getter
@Builder
@JsonDeserialize(builder = RecommendationResponse.RecommendationResponseBuilder.class)
public class RecommendationResponse {

    private List<ActivityRecommendation> activities;

    /**
     * MatchScoreCalculator가 계산한 0~100 점수.
     * 유저 스펙과 유사 합격자 전체를 비교한 단일 점수이므로
     * 개별 활동이 아닌 응답 최상단에 위치한다.
     */
    private int matchScore;

    /** "유사 합격자 N명과 비교한 결과" 또는 "데이터가 부족해 AI 일반 추천을 제공합니다" */
    private String comparisonMessage;

    /** false면 프론트에서 "일반 추천" 배지 표시 */
    private boolean isAiRecommendation;

    @Getter
    @Builder
    @JsonDeserialize(builder = ActivityRecommendation.ActivityRecommendationBuilder.class)
    public static class ActivityRecommendation {

        /** activities 테이블 PK (UUID로 통일 — 기획서 예시의 정수 id 사용 안 함) */
        private UUID id;

        /** INTERNSHIP | EXTERNAL | COMPETITION | EDUCATION*/
        private String type;

        private String name;

        private String reason;

        private LocalDate deadline;
    }
}
