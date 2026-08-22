package com.career.recommendation.dto.recommendation;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.career.recommendation.dto.position.SpecPositionResult;

/**
 * GET /api/v1/recommendations 응답 DTO.
 *
 * isAiRecommendation: Gemini 정상 응답이면 true, Fallback이면 false → 프론트에서 "일반 추천" 배지 표시.
 * comparisonMessage: 유사 합격자 수에 따른 요약 메시지.
 *
 * ⚠️ @Jacksonized가 필수다. Lombok @Builder가 만드는 빌더 메서드는 접두사가 없는데
 * (activities(...), matchScore(...)), Jackson @JsonPOJOBuilder의 기본 접두사는 "with"라서
 * @JsonDeserialize(builder=...)만 손으로 붙이면 JSON의 어떤 프로퍼티도 빌더 메서드와
 * 매칭되지 않는다. Spring Boot 기본 ObjectMapper는 FAIL_ON_UNKNOWN_PROPERTIES가 꺼져 있어
 * 이 매칭 실패가 예외 없이 조용히 "빈 객체"를 만들어버린다 — 캐시(Recommendation.resultJson)를
 * 역직렬화하는 모든 경로가 활동 없음·점수 0·플래그 전부 false인 껍데기를 돌려받으면서도
 * 아무 에러도 남기지 않았다. @Jacksonized가 @JsonDeserialize(builder=...)와
 * @JsonPOJOBuilder(withPrefix="")를 함께 생성해 이 매칭을 정상화한다.
 */
@Getter
@Builder(toBuilder = true)
@Jacksonized
public class RecommendationResponse {

    private List<ActivityRecommendation> activities;

    /**
     * 스펙·목표가 바뀌어 재생성이 필요했지만 하루 갱신 한도(3회)에 막혀 캐시된 추천을
     * 반환한 경우 true. FE가 이 플래그로 "오늘 갱신 횟수를 모두 사용했어요"를 안내한다 —
     * 이 안내가 없으면 사용자는 스펙을 바꿨는데 결과가 그대로인 것을 버그로 인지한다
     * (2026-08-11 실제 사용자 제보). 캐시에 저장되는 응답에는 이 플래그를 싣지 않는다
     * (반환 직전에만 toBuilder로 붙임) — 저장되면 한도가 풀린 다음날에도 계속 true로
     * 내려가는 거짓 안내가 된다. Boolean(nullable)인 이유: 옛 캐시 JSON엔 이 필드가
     * 없어 역직렬화 시 null이 되는데, primitive boolean이면 이 구분이 사라진다.
     */
    private Boolean dailyLimitReached;

    /**
     * 사용자 스펙의 합격자 분포 내 위치·갭 (SpecPositionCalculator 계산 결과).
     * 예전의 matchScore(가중 총점)·compareRows(충족/부족 행)·comparisonMessage·
     * similarPasserCount·unrecognizedCertifications를 전부 이 객체가 대체한다 —
     * 비교 기준 설명은 specPosition.basisMessage, 표본 수는 specPosition.sampleSize,
     * 미매칭 자격증 고지는 specPosition.unmatchedCertifications.
     */
    private SpecPositionResult specPosition;

    /**
     * false면 프론트에서 "일반 추천" 배지 표시.
     *
     * ⚠️ 필드명을 isAiRecommendation이 아니라 aiRecommendation으로 둔다. 필드명이 "is"로
     * 시작하면 Lombok 빌더 메서드도 필드명 그대로 isAiRecommendation(...)이 되는데, Jackson
     * getter 직렬화는 isAiRecommendation()에서 "is"를 떼고 프로퍼티명을 "aiRecommendation"으로
     * 쓴다(JavaBean boolean 관례). 그러면 직렬화 키("aiRecommendation")와 빌더 메서드명
     * ("isAiRecommendation")이 어긋나 @Jacksonized를 붙여도 이 필드만 역직렬화에서 계속
     * 빠진다. 필드명을 aiRecommendation으로 두면 getter는 그대로 isAiRecommendation()이라
     * API 응답 필드명(getter 기준 직렬화)도 안 바뀌고, 빌더 메서드명(aiRecommendation)이
     * 직렬화 키와 정확히 일치해 역직렬화도 된다.
     */
    private boolean aiRecommendation;

    /** 비교 대상 직무명 (예: BACKEND) */
    private String targetJobName;

    /**
     * specPosition을 계산한 공식 버전 (SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION).
     * 옛 캐시 JSON엔 이 필드가 없거나(역직렬화 시 null) 낮은 버전(v8 이하 — 구 점수 체계)이
     * 들어 있고, RecommendationService가 이를 legacy로 판정해 다음 요청에서 한 번 강제로
     * 재계산한다. 계산 방식이 바뀔 때마다 버전을 올리지 않으면 배포 후에도 유저마다
     * 옛 방식/새 방식 결과가 뒤섞여 보이게 된다.
     */
    private Integer scoreFormulaVersion;

    @Getter
    @Builder
    @Jacksonized
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
