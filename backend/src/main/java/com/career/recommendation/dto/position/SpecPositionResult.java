package com.career.recommendation.dto.position;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * BE-1 담당 — 사용자 스펙의 "합격자 분포 내 위치"와 "갭" 계산 결과.
 * 예전 MatchScoreResult(가중 총점 + 충족/부족 비교 행)를 대체한다.
 *
 * 점수 하나로 뭉치지 않는 이유: 축 간 가중치(예전 40/33/27)는 데이터에서 나온 값이
 * 아니라 임의로 정한 값이라, 총점의 의미를 설명할 수 없었다. percentile 위치는
 * "합격자 중 몇 % 지점"이라는 자체 설명이 되는 값이고, 축마다 독립이라 가중치가
 * 필요 없다. 갭 리스트는 "다음에 뭘 하면 되는지"를 직접 말한다.
 *
 * ⚠️ 캐시(Recommendation.resultJson)에 함께 직렬화되므로 @Jacksonized가 필수다
 * (RecommendationResponse의 동일 주석 참고 — 없으면 역직렬화가 조용히 빈 객체를 만든다).
 */
@Getter
@Builder
@Jacksonized
public class SpecPositionResult {

    /** 비교 기준: JOB(목표 직무 프로필) | OVERALL(전체 합격자 폴백) | NONE(데이터 부족) */
    private String basis;

    /** FE가 그대로 띄우는 비교 기준 설명. 폴백을 탔으면 그 사실을 정직하게 말한다. */
    private String basisMessage;

    /** 비교에 쓴 프로필의 합격자 수. basis가 NONE이면 0. */
    private Integer sampleSize;

    /** 축별 위치. 합격자 데이터가 없는 축은 아예 포함하지 않는다(예전 v8 규칙 유지). */
    private List<AxisPosition> axes;

    /** 갭: 이 직무 합격자 다수가 보유하지만 사용자에게 없는 자격증. 보유율 내림차순. */
    private List<SpecGap> gaps;

    /** 사용자 보유 자격증 중 프로필에도 등장하는 것(합격자 표기 기준). 체크리스트 "충족" 표시용. */
    private List<String> matchedCertifications;

    /**
     * 사용자 보유 자격증 중 이 프로필의 합격자 누구도 갖고 있지 않은 것(사용자 원본 표기).
     * "무시했다"가 아니라 "이 직무 합격자 기준으로는 비교 대상이 없다"는 고지다 —
     * 오타라면 사용자가 여기서 알아채고 고칠 수 있다(예전 미인식 배너의 역할 계승).
     */
    private List<String> unmatchedCertifications;

    @Getter
    @Builder
    @Jacksonized
    public static class AxisPosition {

        /** GPA | LANGUAGE | CERTIFICATION | EXPERIENCE */
        private String axis;

        /** 화면 표시용 축 이름: "학점", "어학 성적", "자격증", "경험" */
        private String label;

        /** 사용자 값 표시 ("3.80/4.5", "환산 900", "2개") 또는 "미입력" */
        private String myValue;

        /** 합격자 중앙값 표시. 평균이 아닌 중앙값 — 소표본에서 극단값에 덜 휘둘린다. */
        private String medianValue;

        /**
         * 합격자 분포 내 사용자 위치(0~100, midrank). null이면 사용자 미입력 —
         * 미입력을 0으로 그리면 "최하위"와 구분이 안 되므로 반드시 null로 둔다.
         */
        private Integer percentile;

        /** 이 축 데이터를 실제로 가진 합격자 수. FE가 "N명 기준" 각주를 달 수 있게 한다. */
        private Integer coverage;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class SpecGap {

        /** 합격자들이 가장 많이 쓴 원본 표기 (화면 표시용) */
        private String name;

        /** 이 직무 합격자 보유율(%). "합격자 70%가 보유" 문구의 근거. */
        private Integer holderRatePercent;
    }
}
