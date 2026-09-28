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

    /**
     * 사용자의 목표 직무 코드(JobType.name())·한글 라벨. 직무 미설정이면 null.
     * FE가 basisMessage를 파싱하지 않고 "백엔드 합격자 제보하기" 같은 CTA를 만들 수 있게 명시한다.
     */
    private String targetJobType;
    private String targetJobLabel;

    /**
     * 목표 직무 프로필의 실제 합격자 수 — 비교에 쓰였는지와 무관하게 항상 채운다.
     * basis가 OVERALL/NONE일 때 "백엔드 합격자 1명 (3명부터 비교 가능)"처럼 부족한 정도를
     * 보여주고 제보를 유도하는 데 쓴다. 직무 미설정이면 0.
     */
    private Integer jobSampleSize;

    /** 직무 프로필로 비교하기 위한 최소 표본 수(SpecPositionCalculator.MIN_SAMPLE). */
    private Integer minSampleSize;

    /** 비교에 쓴 프로필에 합성 DEMO(또는 출처 미상) 데이터가 포함되었는지. FE 고지용. */
    private Boolean demoDataIncluded;

    /** 축별 위치. 합격자 데이터가 없는 축은 아예 포함하지 않는다(예전 v8 규칙 유지). */
    private List<AxisPosition> axes;

    /**
     * E11-2 — 목표 직무의 요구 영역 커버리지. percentile 축이 아니다 — 점수에 반영하지 않고
     * 화면에 별도 섹션으로만 표시한다. 목표 직무가 미설정이면 null(FE와 확정된 계약). 합격자
     * 표본(axes/gaps의 basis)과 무관하게(basis가 NONE이어도) 사용자 경험의 areas 합집합만으로
     * 보유 여부를 판정하므로 항상 채울 수 있으면 채운다.
     *
     * 목표 직무 프로필의 {@code githubSampleSize}가 {@link com.career.recommendation.util.SpecPositionCalculator#MIN_SAMPLE}
     * 이상이면 2차(합격자 areas 분포, {@link #coverageSource}=PASSER_DISTRIBUTION) — 보유율 상위
     * N개를 보유율 내림차순으로 담고, 각 항목의 {@link AreaCoverage#passerRatio}를 채운다.
     * 미만이면 1차(고정 체크리스트, coverageSource=CHECKLIST) — JobAreaRequirements 선언 순서
     * 그대로, passerRatio는 null.
     */
    private List<AreaCoverage> areaCoverage;

    /**
     * E11-2(2차) — areaCoverage를 채운 방식. PASSER_DISTRIBUTION(합격자 areas 실측 분포) |
     * CHECKLIST(1차 고정 체크리스트 폴백). areaCoverage가 null이면(목표 직무 미설정) 이 필드도 null.
     * 옛 캐시 응답엔 필드 자체가 없을 수 있다 — FE는 undefined를 CHECKLIST와 같게 취급한다.
     */
    private String coverageSource;

    /**
     * E11-2(2차) — coverageSource가 PASSER_DISTRIBUTION일 때 그 분포의 분모
     * (github_derived가 있는, 즉 GitHub 아이디를 제보·동의해 분석이 성공한 합격자 수 —
     * JobSpecProfile.githubSampleSize와 동일). CHECKLIST 폴백이거나 areaCoverage가 null이면 null.
     */
    private Integer coverageSampleSize;

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

    /**
     * E11-2 요구 영역 하나의 보유 여부. FE 확정 계약(1차):
     * {"area":"API","label":"API 개발","covered":true}
     * 2차(PASSER_DISTRIBUTION)에서는 passerRatio도 함께 채워진다.
     */
    @Getter
    @Builder
    @Jacksonized
    public static class AreaCoverage {

        /** ExperienceArea 코드 (예: API) */
        private String area;

        /** 화면 표시용 한글 라벨 (예: API 개발) */
        private String label;

        /** 사용자 experiences의 areas 합집합에 이 영역이 포함되는지 */
        private boolean covered;

        /**
         * E11-2(2차) — 이 영역을 보유한 합격자 비율(0~1). coverageSource가 CHECKLIST(1차 폴백)일
         * 때는 실측 보유율이 없으므로 null. 옛 캐시 응답엔 필드 자체가 없을 수 있다(undefined).
         */
        private Double passerRatio;
    }
}
