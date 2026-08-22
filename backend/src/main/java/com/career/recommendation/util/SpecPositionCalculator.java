package com.career.recommendation.util;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.dto.position.JobSpecProfile.CertStat;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.position.SpecPositionResult.AxisPosition;
import com.career.recommendation.dto.position.SpecPositionResult.SpecGap;
import com.career.recommendation.entity.UserSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BE-1 담당 — 사용자 스펙을 직무 요구 프로필(JobSpecProfile)과 비교해
 * 축별 percentile 위치와 갭 리스트를 계산한다.
 *
 * 예전 체계(SimilarSpecFinder + MatchScoreCalculator)와의 차이:
 *   - Top 5 유사 합격자 "평균 대비 비율" → 직무 합격자 "전체 분포 내 위치(percentile)".
 *     비율 점수는 표본 5명 평균에 얼마나 가까운가일 뿐 의미를 설명할 수 없었고,
 *     바닥값·스케일 상수·축 가중치 같은 보정 상수를 계속 요구했다(v1~v8 이력).
 *     percentile은 자체 설명이 되는 값이라 그 상수들이 전부 필요 없다.
 *   - 축 간 가중치(40/33/27) 없음 — 축마다 독립된 위치 정보를 그대로 보여준다.
 *   - 자격증 "인식 3층 + 가중치 합산" → "프로필 등장 여부 + 갭 리스트".
 *     정크 입력은 프로필에 없으므로 자연히 위치에 기여하지 못하고(예전 v3 불변식과
 *     같은 효과), 실재하지만 이 직무에 없는 자격증은 unmatched로 정직하게 고지한다.
 *
 * 유지한 원칙(공식이 아니라 원칙이므로 계승):
 *   - 결측은 중립 — 합격자 결측은 분포에서 제외되고(프로필 집계 단계), 데이터 없는 축은
 *     행 자체를 만들지 않는다.
 *   - 표본 미달 비교는 보여주지 않는다 — MIN_SAMPLE 미만이면 다음 폴백(전체 프로필),
 *     그래도 미달이면 "데이터 부족"을 그대로 말한다.
 *   - 사용자 미입력은 0점이 아니라 "미입력" — percentile을 null로 둬서 최하위와 구분한다.
 */
@Component
public class SpecPositionCalculator {

    /**
     * 위치·갭을 계산한 공식 버전. 예전 MatchScoreCalculator의 번호(마지막 8)를 이어받는다 —
     * RecommendationService가 캐시된 응답의 scoreFormulaVersion과 비교해 옛 버전 캐시를
     * legacy로 판정하는 체계를 그대로 쓰므로, 번호를 이어야 v8 이하(구 점수 체계) 캐시가
     * 전부 재계산 대상이 된다.
     *
     * 9: 가중 총점(matchScore)·비교 행(compareRows)을 percentile 위치(specPosition)로 교체.
     */
    public static final int CURRENT_SCORE_FORMULA_VERSION = 9;

    public static final String BASIS_JOB = "JOB";
    public static final String BASIS_OVERALL = "OVERALL";
    public static final String BASIS_NONE = "NONE";

    /**
     * 비교 결과를 신뢰하기 위한 최소 표본 수(예전 SimilarSpecFinder.MIN_SAMPLE 원칙 계승).
     * 미만이면 그 프로필로는 비교하지 않는다 — 1~2명 분포에서의 percentile은 그 한두 명의
     * 개인 스펙에 좌우되는 무의미한 값인데 5명 비교와 똑같은 확신으로 그려진다.
     */
    private static final int MIN_SAMPLE = 3;

    /**
     * 갭으로 보여줄 최소 보유율. 이 값 미만은 "합격자 다수가 가진 것"이라 말할 수 없다.
     * 값 근거: 직무당 합격자 10명 안팎(운영 60명/6직무)에서 2명 이상이 가진 것부터 잡히는
     * 수준. 데이터가 쌓이면 함께 재검토한다.
     */
    private static final double GAP_MIN_HOLDER_RATE = 0.2;

    /** 갭 리스트 최대 길이. "다음 할 일"은 짧아야 행동으로 이어진다. */
    private static final int MAX_GAPS = 5;

    /** 학점 표시용 4.5 환산 기준. */
    private static final double GPA_DISPLAY_SCALE = 4.5;

    /** 부동소수 percentile 동률 판정 오차. */
    private static final double EPS = 1e-9;

    /**
     * @param jobProfile     목표 직무 프로필 (직무 미설정이면 빈 프로필)
     * @param overallProfile 전체 합격자 프로필 (직무 표본 미달 시 폴백)
     */
    public SpecPositionResult calculate(UserSpec userSpec,
                                        JobSpecProfile jobProfile, JobSpecProfile overallProfile) {
        JobSpecProfile profile;
        String basis;
        if (jobProfile != null && jobProfile.getJobType() != null && jobProfile.getSampleSize() >= MIN_SAMPLE) {
            profile = jobProfile;
            basis = BASIS_JOB;
        } else if (overallProfile != null && overallProfile.getSampleSize() >= MIN_SAMPLE) {
            profile = overallProfile;
            basis = BASIS_OVERALL;
        } else {
            return SpecPositionResult.builder()
                    .basis(BASIS_NONE)
                    .basisMessage("아직 비교할 합격자 데이터가 부족합니다.")
                    .sampleSize(0)
                    .demoDataIncluded(false)
                    .axes(List.of())
                    .gaps(List.of())
                    .matchedCertifications(List.of())
                    .unmatchedCertifications(userCertDisplayList(userSpec, null))
                    .build();
        }

        Set<String> userCerts = SpecNormalizer.canonicalCerts(
                userSpec != null ? userSpec.getCertifications() : null);

        return SpecPositionResult.builder()
                .basis(basis)
                .basisMessage(basisMessage(basis, jobProfile, profile))
                .sampleSize(profile.getSampleSize())
                .demoDataIncluded(profile.isContainsDemoData())
                .axes(buildAxes(userSpec, userCerts, profile))
                .gaps(buildGaps(userCerts, profile))
                .matchedCertifications(matchedCerts(userCerts, profile))
                .unmatchedCertifications(userCertDisplayList(userSpec, profile))
                .build();
    }

    private String basisMessage(String basis, JobSpecProfile jobProfile, JobSpecProfile used) {
        if (BASIS_JOB.equals(basis)) {
            return String.format("%s 합격자 %d명의 분포와 비교한 결과입니다.",
                    used.getJobType(), used.getSampleSize());
        }
        // 전체 폴백 — 원인(직무 미설정 vs 직무 데이터 부족)을 구분해 정직하게 말한다.
        // 예전 buildComparisonMessage가 폴백 사실을 숨기고 직무명을 단언하던 문제의 재발 방지.
        String jobType = jobProfile != null ? jobProfile.getJobType() : null;
        if (jobType == null || jobType.isBlank()) {
            return String.format("목표 직무 미설정 상태로, 전체 합격자 %d명의 분포와 비교한 결과입니다.",
                    used.getSampleSize());
        }
        return String.format("%s 합격자 데이터가 부족해, 직무 구분 없이 전체 합격자 %d명의 분포와 비교한 결과입니다.",
                jobType, used.getSampleSize());
    }

    /**
     * 축별 위치. 합격자 데이터가 없는 축은 행을 만들지 않고, 사용자 미입력은
     * percentile null + "미입력"으로 만든다 — 이 둘을 섞으면 예전의 점수-화면 모순이 재발한다.
     */
    private List<AxisPosition> buildAxes(UserSpec userSpec, Set<String> userCerts, JobSpecProfile profile) {
        List<AxisPosition> axes = new ArrayList<>(4);

        // --- 학점 ---
        double[] gpaRatios = profile.getGpaRatios();
        if (gpaRatios.length > 0) {
            boolean hasGpa = userSpec != null && userSpec.getGpa() != null
                    && userSpec.getGpaMax() != null && userSpec.getGpaMax().signum() > 0;
            double userRatio = hasGpa
                    ? userSpec.getGpa().doubleValue() / userSpec.getGpaMax().doubleValue() : 0.0;
            axes.add(AxisPosition.builder()
                    .axis("GPA").label("학점")
                    .myValue(hasGpa ? String.format("%.2f/4.5", userRatio * GPA_DISPLAY_SCALE) : "미입력")
                    .medianValue(String.format("%.2f/4.5", median(gpaRatios) * GPA_DISPLAY_SCALE))
                    .percentile(hasGpa ? percentileOf(gpaRatios, userRatio) : null)
                    .coverage(gpaRatios.length)
                    .build());
        }

        // --- 어학 ---
        double[] toeics = profile.getToeicEquivalents();
        if (toeics.length > 0) {
            double userToeic = SpecNormalizer.maxEquivalentToeic(
                    userSpec != null ? userSpec.getLanguageScores() : null);
            axes.add(AxisPosition.builder()
                    .axis("LANGUAGE").label("어학 성적")
                    .myValue(userToeic > 0 ? String.format("환산 %d", (int) userToeic) : "미입력")
                    .medianValue(String.format("환산 %d", (int) median(toeics)))
                    .percentile(userToeic > 0 ? percentileOf(toeics, userToeic) : null)
                    .coverage(toeics.length)
                    .build());
        }

        // --- 자격증 ---
        // 사용자 개수는 "프로필에 등장하는 자격증"만 센다 — 검증된 합격자 데이터와 달리
        // 사용자 입력은 자유 텍스트라, 프로필 등장 여부가 실재 검증을 대신한다(정크 입력은
        // 자연히 0). 프로필 밖 실재 자격증은 unmatchedCertifications로 별도 고지되므로
        // 조용히 사라지지 않는다.
        int[] certCounts = profile.getCertCounts();
        if (certCounts.length > 0) {
            int matched = (int) userCerts.stream().filter(profile::containsCert).count();
            axes.add(AxisPosition.builder()
                    .axis("CERTIFICATION").label("자격증")
                    .myValue(String.format("%d개", matched))
                    .medianValue(String.format("%.1f개", median(certCounts)))
                    .percentile(percentileOf(certCounts, matched))
                    .coverage(certCounts.length)
                    .build());
        }

        // --- 경험 ---
        // UserSpec에는 아직 경험 필드가 없어 사용자 쪽은 항상 "미입력"이다. 그래도 축을
        // 보여주는 이유: "이 직무 합격자는 경험이 중앙값 N개"라는 정보 자체가 사용자에게
        // 유효하고, 경험 입력 기능이 붙는 순간 여기 percentile만 연결하면 끝나는 구조를
        // 미리 잡아두기 위함이다.
        int[] expCounts = profile.getExperienceCounts();
        if (expCounts.length > 0) {
            axes.add(AxisPosition.builder()
                    .axis("EXPERIENCE").label("경험")
                    .myValue("미입력")
                    .medianValue(String.format("%.1f개", median(expCounts)))
                    .percentile(null)
                    .coverage(expCounts.length)
                    .build());
        }

        return axes;
    }

    /** 보유율 임계 이상인데 사용자가 없는 자격증, 보유율 내림차순 상위 MAX_GAPS개. */
    private List<SpecGap> buildGaps(Set<String> userCerts, JobSpecProfile profile) {
        List<SpecGap> gaps = new ArrayList<>(MAX_GAPS);
        for (CertStat stat : profile.getCertStats()) {   // 이미 보유율 내림차순
            if (gaps.size() >= MAX_GAPS) break;
            if (stat.getHolderRate() < GAP_MIN_HOLDER_RATE) break;
            if (userCerts.contains(stat.getCanonicalName())) continue;
            gaps.add(SpecGap.builder()
                    .name(stat.getDisplayName())
                    .holderRatePercent((int) Math.round(stat.getHolderRate() * 100))
                    .build());
        }
        return gaps;
    }

    /** 사용자 보유 자격증 중 프로필에도 등장하는 것 — 합격자 최빈 표기로 통일해서 반환. */
    private List<String> matchedCerts(Set<String> userCerts, JobSpecProfile profile) {
        List<String> matched = new ArrayList<>();
        for (CertStat stat : profile.getCertStats()) {   // 보유율 내림차순 유지
            if (userCerts.contains(stat.getCanonicalName())) {
                matched.add(stat.getDisplayName());
            }
        }
        return matched;
    }

    /**
     * 사용자 자격증 중 프로필에 없는 것의 원본 표기 목록(정규화 기준 중복 제거, 첫 표기 유지).
     * profile이 null이면(비교 자체가 불가한 NONE) 전부 "비교 대상 없음"으로 나열한다 —
     * 비교를 못 하는 상황에서도 입력이 어떻게 처리됐는지는 알려준다(예전 원칙 계승).
     */
    private List<String> userCertDisplayList(UserSpec userSpec, JobSpecProfile profile) {
        if (userSpec == null || userSpec.getCertifications() == null) return List.of();
        Map<String, String> firstRawByCanonical = new LinkedHashMap<>();
        for (String raw : userSpec.getCertifications()) {
            if (raw == null || raw.isBlank()) continue;
            String canonical = SpecNormalizer.canonicalCert(raw);
            if (canonical.isBlank()) continue;
            if (profile != null && profile.containsCert(canonical)) continue;
            firstRawByCanonical.putIfAbsent(canonical, raw);
        }
        return List.copyOf(firstRawByCanonical.values());
    }

    /**
     * midrank percentile: (미만 개수 + 동률 개수의 절반) / 전체 × 100.
     * 동률을 절반만 세는 이유: 전부 아래로 치면 동률 값이 0%, 전부 위로 치면 100%가 되어
     * "합격자 전원과 같은 값"인 사용자가 극단으로 그려진다. midrank는 그 중간(50%)을 준다.
     */
    static int percentileOf(double[] sorted, double value) {
        int less = 0;
        int equal = 0;
        for (double v : sorted) {
            if (v < value - EPS) less++;
            else if (Math.abs(v - value) <= EPS) equal++;
        }
        return (int) Math.round((less + 0.5 * equal) / sorted.length * 100);
    }

    static int percentileOf(int[] sorted, int value) {
        int less = 0;
        int equal = 0;
        for (int v : sorted) {
            if (v < value) less++;
            else if (v == value) equal++;
        }
        return (int) Math.round((less + 0.5 * equal) / (double) sorted.length * 100);
    }

    static double median(double[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    static double median(int[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
