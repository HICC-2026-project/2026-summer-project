package com.career.recommendation.dto.recommendation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.career.recommendation.dto.recommendation.CompareRowDto;

/**
 * GET /api/v1/recommendations 응답 DTO.
 *
 * isAiRecommendation: Gemini 정상 응답이면 true, Fallback이면 false → 프론트에서 "일반 추천" 배지 표시.
 * comparisonMessage: 유사 합격자 수에 따른 요약 메시지.
 */
@Getter
@Builder
@JsonDeserialize(builder = RecommendationResponse.RecommendationResponseBuilder.class)
public class RecommendationResponse {

    private List<ActivityRecommendation> activities;

    /**
     * MatchScoreCalculator가 계산한 0~100 점수.
     */
    private int matchScore;

    /** "유사 합격자 N명과 비교한 결과" 또는 "데이터가 부족해 AI 일반 추천을 제공합니다" */
    private String comparisonMessage;

    /** false면 프론트에서 "일반 추천" 배지 표시 */
    private boolean isAiRecommendation;

    /** 비교 대상 직무명 (예: BACKEND) */
    private String targetJobName;

    /** 비교 대상 유사 합격자 수 */
    private Integer similarPasserCount;

    /** 항목별 세부 비교 내역 */
    private List<CompareRowDto> compareRows;

    /** 비교 대상에 합성 DEMO 데이터가 한 건이라도 포함되었는지 여부 */
    private Boolean sampleComparisonData;

    /**
     * matchScore·compareRows를 계산한 점수 공식 버전 (MatchScoreCalculator.CURRENT_SCORE_FORMULA_VERSION).
     * 옛 캐시 JSON엔 이 필드가 없어 역직렬화 시 null이 되고, RecommendationService가 이를
     * legacy로 판정해 다음 요청에서 한 번 강제로 재계산한다. 공식을 바꿀 때마다
     * CURRENT_SCORE_FORMULA_VERSION을 올리지 않으면 배포 후에도 유저마다 옛 공식/새 공식
     * 점수가 뒤섞여 보이게 된다.
     */
    private Integer scoreFormulaVersion;

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
