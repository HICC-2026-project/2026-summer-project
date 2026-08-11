package com.career.recommendation.dto.recommendation;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.career.recommendation.dto.recommendation.CompareRowDto;

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
     * MatchScoreCalculator가 계산한 0~100 점수.
     */
    private int matchScore;

    /** "유사 합격자 N명과 비교한 결과" 또는 "데이터가 부족해 AI 일반 추천을 제공합니다" */
    private String comparisonMessage;

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

    /** 비교 대상 유사 합격자 수 */
    private Integer similarPasserCount;

    /** 항목별 세부 비교 내역 */
    private List<CompareRowDto> compareRows;

    /** 비교 대상에 합성 DEMO 데이터가 한 건이라도 포함되었는지 여부 */
    private Boolean sampleComparisonData;

    /**
     * 사용자가 입력했지만 자격증 인식 층(MatchScoreCalculator) 어디에서도 매칭되지 않은
     * 원본 표기 목록. 비어있지 않으면 FE에서 "이 자격증은 점수에 반영되지 않았어요" 같은
     * 안내를 띄워, 오타·특이 표기를 사용자가 스스로 확인·수정할 수 있게 한다.
     */
    private List<String> unrecognizedCertifications;

    /**
     * 인식된 자격증 개수 (canonical 기준 — 비교 탭 자격증 행과 같은 집계).
     * FE 홈 카드가 이 값을 그대로 표시해야 비교 탭과 항상 일치한다. 옛 캐시엔 없어
     * null일 수 있다(그 경우 FE는 입력 개수로 폴백).
     */
    private Integer recognizedCertificationCount;

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
