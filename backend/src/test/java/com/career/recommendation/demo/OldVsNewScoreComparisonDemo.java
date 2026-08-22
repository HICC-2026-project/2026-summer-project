package com.career.recommendation.demo;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.position.SpecPositionResult.AxisPosition;
import com.career.recommendation.dto.position.SpecPositionResult.SpecGap;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.util.JobSpecProfileBuilder;
import com.career.recommendation.util.SpecNormalizer;
import com.career.recommendation.util.SpecPositionCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 옛 점수 체계(v8 — Top 5 평균 대비 가중 총점)와 새 체계(v9 — 분포 내 위치·갭)를
 * 같은 합격자 데이터·같은 사용자로 나란히 돌려 차이를 보여주는 데모.
 *
 * 실행: backend 디렉토리에서
 *   ./mvnw test -Dtest=OldVsNewScoreComparisonDemo
 * (콘솔에 비교표가 출력된다. 통과/실패보다 출력을 읽는 용도의 파일이다.)
 *
 * 옛 계산기(MatchScoreCalculator)는 v9 교체 때 삭제됐으므로, 이 파일 안의
 * OldFormulaV8이 그 공식의 핵심(유사 Top5 검색 → 축별 비율 점수 → 40/33.3/26.7
 * 가중 평균 + 결측 축 재정규화 + 학점 바닥값)을 그대로 옮겨 담고 있다.
 * (직무 유도 자격증 가중치·2/3층 인식은 데모 단순화를 위해 큐레이션 표 폴백 경로만 재현.)
 */
class OldVsNewScoreComparisonDemo {

    // ── 옛 공식(v8)의 축소 재현 ────────────────────────────────────────────────

    /** v8 공식 핵심 재현. 상수·분기 구조는 삭제 직전 MatchScoreCalculator와 동일하다. */
    static class OldFormulaV8 {
        static final double WEIGHT_GPA = 0.40, WEIGHT_LANG = 1.0 / 3.0, WEIGHT_CERT = 4.0 / 15.0;
        static final double GPA_FLOOR_RATIO = 0.5;
        static final Map<String, Double> CERT_WEIGHTS = Map.of(
                "정보처리기사", 2.0, "SQLD", 1.5, "ADSP", 1.0, "웹디자인기능사", 0.5);

        /** SimilarSpecFinder 재현: 정규화 학점 비율이 가장 가까운 Top 5. */
        static List<PasserData> top5Similar(UserSpec user, List<PasserData> all) {
            double userRatio = gpaRatio(user.getGpa(), user.getGpaMax());
            return all.stream()
                    .sorted(Comparator.comparingDouble(p ->
                            Math.abs(gpaRatio(p.getGpa(), p.getGpaMax()) - userRatio)))
                    .limit(5)
                    .toList();
        }

        static int totalScore(UserSpec user, List<PasserData> top5) {
            double userRatio = gpaRatio(user.getGpa(), user.getGpaMax());
            boolean userHasGpa = user.getGpa() != null && user.getGpaMax() != null;
            double userToeic = SpecNormalizer.maxEquivalentToeic(user.getLanguageScores());
            double userCert = certValue(user.getCertifications());

            OptionalDouble gpaAxis = top5.stream()
                    .filter(p -> p.getGpa() != null && p.getGpaMax() != null)
                    .mapToDouble(p -> scoreGpa(userHasGpa, userRatio, gpaRatio(p.getGpa(), p.getGpaMax())))
                    .average();
            OptionalDouble langAxis = top5.stream()
                    .mapToDouble(p -> SpecNormalizer.maxEquivalentToeic(p.getLanguageScores()))
                    .filter(v -> v > 0)
                    .map(passerToeic -> ratioScore(userToeic, passerToeic))
                    .average();
            OptionalDouble certAxis = top5.stream()
                    .mapToDouble(p -> certValue(p.getCertifications()))
                    .filter(v -> v > 0)
                    .map(passerCert -> ratioScore(userCert, passerCert))
                    .average();

            double sum = 0, used = 0;
            if (gpaAxis.isPresent()) { sum += gpaAxis.getAsDouble() * WEIGHT_GPA; used += WEIGHT_GPA; }
            if (langAxis.isPresent()) { sum += langAxis.getAsDouble() * WEIGHT_LANG; used += WEIGHT_LANG; }
            if (certAxis.isPresent()) { sum += certAxis.getAsDouble() * WEIGHT_CERT; used += WEIGHT_CERT; }
            return used > 0 ? (int) Math.round(sum / used) : 0;
        }

        /** 축별 소점수도 함께 보여주기 위한 헬퍼. [gpa, lang, cert] (없으면 -1). */
        static double[] axisScores(UserSpec user, List<PasserData> top5) {
            double userRatio = gpaRatio(user.getGpa(), user.getGpaMax());
            boolean userHasGpa = user.getGpa() != null && user.getGpaMax() != null;
            double userToeic = SpecNormalizer.maxEquivalentToeic(user.getLanguageScores());
            double userCert = certValue(user.getCertifications());
            double gpa = top5.stream()
                    .filter(p -> p.getGpa() != null && p.getGpaMax() != null)
                    .mapToDouble(p -> scoreGpa(userHasGpa, userRatio, gpaRatio(p.getGpa(), p.getGpaMax())))
                    .average().orElse(-1);
            double lang = top5.stream()
                    .mapToDouble(p -> SpecNormalizer.maxEquivalentToeic(p.getLanguageScores()))
                    .filter(v -> v > 0)
                    .map(t -> ratioScore(userToeic, t))
                    .average().orElse(-1);
            double cert = top5.stream()
                    .mapToDouble(p -> certValue(p.getCertifications()))
                    .filter(v -> v > 0)
                    .map(c -> ratioScore(userCert, c))
                    .average().orElse(-1);
            return new double[]{gpa, lang, cert};
        }

        static double gpaRatio(BigDecimal gpa, BigDecimal gpaMax) {
            if (gpa == null || gpaMax == null || gpaMax.signum() <= 0) return 0;
            return gpa.doubleValue() / gpaMax.doubleValue();
        }

        static double scoreGpa(boolean userHasGpa, double userRatio, double passerRatio) {
            if (!userHasGpa) return 0;
            if (passerRatio <= 0) return 100;
            if (userRatio >= passerRatio) return 100;
            double span = passerRatio - GPA_FLOOR_RATIO;
            if (span <= 0) return Math.max(0, userRatio / passerRatio * 100);
            return Math.max(0, (userRatio - GPA_FLOOR_RATIO) / span * 100);
        }

        static double ratioScore(double userValue, double passerValue) {
            if (userValue <= 0) return 0;
            return Math.min(100, userValue / passerValue * 100);
        }

        static double certValue(String[] certs) {
            return SpecNormalizer.canonicalCerts(certs).stream()
                    .mapToDouble(c -> CERT_WEIGHTS.getOrDefault(c, 0.0))
                    .sum();
        }
    }

    // ── 공통 데이터: BACKEND 합격자 10명 ──────────────────────────────────────

    private static final List<PasserData> PASSERS = List.of(
            passer("3.20", 750, 1, "정보처리기사"),
            passer("3.35", 780, 2, "정보처리기사"),
            passer("3.50", 800, 2, "정보처리기사", "SQLD"),
            passer("3.60", 820, 3, "SQLD"),
            passer("3.70", 850, 2, "정보처리기사"),
            passer("3.80", 860, 3, "정보처리기사", "SQLD"),
            passer("3.90", 880, 4, "정보처리기사", "ADsP"),
            passer("4.00", 900, 3, "정보처리기사", "SQLD"),
            passer("4.10", 930, 5, "ADsP"),
            passer("4.30", 950, 4));

    private final JobSpecProfileBuilder profileBuilder = new JobSpecProfileBuilder();
    private final SpecPositionCalculator positionCalculator = new SpecPositionCalculator();

    @Test
    void 옛_점수와_새_위치를_나란히_비교한다() {
        JobSpecProfile profile = profileBuilder.build("BACKEND", PASSERS);

        UserSpec average = user("3.60", 850, "SQLD");
        UserSpec highGpaOnly = user("4.30", 0);                       // 고학점, 어학·자격증 없음
        UserSpec lowGpaCerts = user("3.00", 700, "정보처리기사", "SQLD", "ADsP"); // 저학점, 자격증 부자
        UserSpec withJunk = user("3.60", 850, "SQLD", "정크자격1", "정크자격2");

        printCase("A. 평균 근처 (3.60 / 토익 850 / SQLD)", average, profile);
        printCase("B. 고학점·나머지 미입력 (4.30)", highGpaOnly, profile);
        printCase("C. 저학점·자격증 3개 (3.00 / 700 / 기사+SQLD+ADsP)", lowGpaCerts, profile);
        printCase("D. A + 정크 자격증 2개", withJunk, profile);

        // ── 문서화를 겸한 핵심 차이 단언 ──────────────────────────────────────

        // 1) 옛 공식의 순환성: 비교 대상이 "내 학점 근처 Top5"라, 코호트 꼴찌(C, 3.00)도
        //    학점 소점수가 후하게 나온다. 새 체계는 전체 분포 기준이라 하위 0%로 정직하다.
        double oldGpaScoreOfC = OldFormulaV8.axisScores(lowGpaCerts, OldFormulaV8.top5Similar(lowGpaCerts, PASSERS))[0];
        int newGpaPctOfC = axisPercentile(lowGpaCerts, profile, "GPA");
        assertThat(oldGpaScoreOfC).isGreaterThan(50);   // 옛: 꼴찌인데도 50점 초과
        assertThat(newGpaPctOfC).isZero();              // 새: 하위 0% — 사실 그대로

        // 2) 옛 공식의 포화: 학점이 비교 대상 평균만 넘으면 무조건 100이라 "조금 높음"과
        //    "압도적으로 높음"이 구분되지 않았다. 새 percentile은 위쪽 구간도 정보를 가진다.
        double oldGpaScoreOfB = OldFormulaV8.axisScores(highGpaOnly, OldFormulaV8.top5Similar(highGpaOnly, PASSERS))[0];
        assertThat(oldGpaScoreOfB).isEqualTo(100.0);
        assertThat(axisPercentile(highGpaOnly, profile, "GPA")).isGreaterThanOrEqualTo(95);

        // 3) 정크 입력 중립성은 두 체계 모두 지킨다(v3 불변식 계승) — 점수/위치가 A와 동일.
        SpecPositionResult posA = positionCalculator.calculate(average, profile, () -> null);
        SpecPositionResult posD = positionCalculator.calculate(withJunk, profile, () -> null);
        assertThat(axisPercentile(posD, "CERTIFICATION")).isEqualTo(axisPercentile(posA, "CERTIFICATION"));
        // 다만 새 체계는 "왜 반영 안 됐는지"를 목록으로 돌려준다.
        assertThat(posD.getUnmatchedCertifications()).containsExactly("정크자격1", "정크자격2");

        // 4) 새 체계만 주는 것: 다음 할 일(갭). A는 SQLD 보유 → 갭 1순위는 정보처리기사(70%).
        assertThat(posA.getGaps()).isNotEmpty();
        assertThat(posA.getGaps().get(0).getName()).isEqualTo("정보처리기사");
        assertThat(posA.getGaps().get(0).getHolderRatePercent()).isEqualTo(70);
    }

    // ── 출력 ─────────────────────────────────────────────────────────────────

    private void printCase(String title, UserSpec user, JobSpecProfile profile) {
        List<PasserData> top5 = OldFormulaV8.top5Similar(user, PASSERS);
        int oldTotal = OldFormulaV8.totalScore(user, top5);
        double[] oldAxes = OldFormulaV8.axisScores(user, top5);
        SpecPositionResult pos = positionCalculator.calculate(user, profile, () -> null);

        System.out.println();
        System.out.println("━━ " + title);
        System.out.printf("  [옛 v8] 종합 %d점  (학점 %s · 어학 %s · 자격증 %s — 가중 40/33.3/26.7)%n",
                oldTotal, fmt(oldAxes[0]), fmt(oldAxes[1]), fmt(oldAxes[2]));
        System.out.println("  [새 v9] " + pos.getBasisMessage());
        for (AxisPosition a : pos.getAxes()) {
            System.out.printf("     %-6s 내 %s / 중앙값 %s → %s (합격자 %d명 기준)%n",
                    a.getLabel(), a.getMyValue(), a.getMedianValue(),
                    a.getPercentile() == null ? "미입력" : "percentile " + a.getPercentile() + " (상위 " + (100 - a.getPercentile()) + "%)",
                    a.getCoverage());
        }
        if (!pos.getGaps().isEmpty()) {
            System.out.print("     갭:");
            for (SpecGap g : pos.getGaps()) {
                System.out.printf(" %s(합격자 %d%% 보유)", g.getName(), g.getHolderRatePercent());
            }
            System.out.println();
        }
        if (!pos.getUnmatchedCertifications().isEmpty()) {
            System.out.println("     비교 미반영 자격증: " + String.join(", ", pos.getUnmatchedCertifications()));
        }
    }

    private static String fmt(double axisScore) {
        return axisScore < 0 ? "—" : String.format("%.0f", axisScore);
    }

    private int axisPercentile(UserSpec user, JobSpecProfile profile, String axis) {
        return axisPercentile(positionCalculator.calculate(user, profile, () -> null), axis);
    }

    private int axisPercentile(SpecPositionResult pos, String axis) {
        return pos.getAxes().stream()
                .filter(a -> a.getAxis().equals(axis))
                .map(AxisPosition::getPercentile)
                .findFirst()
                .orElseThrow();
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private static PasserData passer(String gpa, int toeic, int expCount, String... certs) {
        return PasserData.builder()
                .jobType("BACKEND")
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", toeic)))
                .certifications(certs)
                .experienceCount(expCount)
                .isVerified(true)
                .dataOrigin("PUBLIC_REVIEW")
                .build();
    }

    private static UserSpec user(String gpa, int toeic, String... certs) {
        return UserSpec.builder()
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .languageScores(toeic > 0 ? List.of(Map.of("type", "TOEIC", "score", toeic)) : List.of())
                .certifications(certs)
                .build();
    }
}
