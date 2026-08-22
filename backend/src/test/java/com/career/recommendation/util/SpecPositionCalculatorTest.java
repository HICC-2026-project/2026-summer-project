package com.career.recommendation.util;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.position.SpecPositionResult.AxisPosition;
import com.career.recommendation.dto.position.SpecPositionResult.SpecGap;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecPositionCalculatorTest {

    private final SpecPositionCalculator calculator = new SpecPositionCalculator();
    private final JobSpecProfileBuilder profileBuilder = new JobSpecProfileBuilder();

    // --- 기준 프로필 선택 (JOB → OVERALL → NONE 폴백) ---

    @Test
    void 직무_표본이_3명_이상이면_직무_프로필을_기준으로_쓴다() {
        JobSpecProfile job = profile("BACKEND",
                passer("3.50", 800, "SQLD"), passer("3.80", 850, "SQLD"), passer("4.00", 900, "SQLD"));
        JobSpecProfile overall = profile(null,
                passer("2.00", 500), passer("2.50", 550), passer("3.00", 600));

        SpecPositionResult result = calculator.calculate(user("3.80", 850), job, () -> overall);

        assertThat(result.getBasis()).isEqualTo("JOB");
        assertThat(result.getSampleSize()).isEqualTo(3);
        assertThat(result.getBasisMessage()).contains("BACKEND", "3명");
    }

    @Test
    void 직무_표본이_미달이면_전체_프로필로_폴백하고_그_사실을_말한다() {
        // 표본 1~2명 분포의 percentile은 그 한두 명의 개인 스펙에 좌우되는 무의미한 값 —
        // 예전 SimilarSpecFinder.MIN_SAMPLE과 같은 원칙.
        JobSpecProfile job = profile("BACKEND", passer("3.50", 800));
        JobSpecProfile overall = profile(null,
                passer("3.00", 700), passer("3.50", 800), passer("4.00", 900));

        SpecPositionResult result = calculator.calculate(user("3.80", 850), job, () -> overall);

        assertThat(result.getBasis()).isEqualTo("OVERALL");
        assertThat(result.getBasisMessage()).contains("BACKEND", "부족", "전체 합격자 3명");
    }

    @Test
    void 직무_미설정이면_전체_프로필_기준임을_다른_문구로_말한다() {
        JobSpecProfile emptyJob = profile(null); // 직무 미설정 → 빈 프로필
        JobSpecProfile overall = profile(null,
                passer("3.00", 700), passer("3.50", 800), passer("4.00", 900));

        SpecPositionResult result = calculator.calculate(user("3.80", 850), emptyJob, () -> overall);

        assertThat(result.getBasis()).isEqualTo("OVERALL");
        assertThat(result.getBasisMessage()).contains("목표 직무 미설정");
    }

    @Test
    void 전체_표본도_미달이면_NONE과_빈_축을_반환한다() {
        JobSpecProfile job = profile("BACKEND", passer("3.50", 800));
        JobSpecProfile overall = profile(null, passer("3.00", 700), passer("3.50", 800));

        SpecPositionResult result = calculator.calculate(user("3.80", 850), job, () -> overall);

        assertThat(result.getBasis()).isEqualTo("NONE");
        assertThat(result.getAxes()).isEmpty();
        assertThat(result.getGaps()).isEmpty();
        assertThat(result.getSampleSize()).isZero();
    }

    // --- percentile 위치 ---

    @Test
    void 분포_중앙값과_같으면_50이다() {
        // midrank: 3명(3.0, 3.5, 4.0) 중 3.5와 동일 → (1 + 0.5) / 3 = 50%
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700), passer("3.50", 800), passer("4.00", 900));

        SpecPositionResult result = calculator.calculate(user("3.50", 800), job, () -> null);

        AxisPosition gpa = axis(result, "GPA");
        assertThat(gpa.getPercentile()).isEqualTo(50);
        assertThat(axis(result, "LANGUAGE").getPercentile()).isEqualTo(50);
    }

    @Test
    void 전원보다_높으면_100_전원보다_낮으면_0이다() {
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700), passer("3.50", 800), passer("4.00", 900));

        SpecPositionResult top = calculator.calculate(user("4.40", 990), job, () -> null);
        SpecPositionResult bottom = calculator.calculate(user("2.00", 400), job, () -> null);

        assertThat(axis(top, "GPA").getPercentile()).isEqualTo(100);
        assertThat(axis(bottom, "GPA").getPercentile()).isZero();
    }

    @Test
    void 사용자_미입력_축은_percentile이_null이고_미입력으로_표시된다() {
        // 미입력을 0으로 그리면 "전원보다 낮음"과 구분되지 않는다.
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700), passer("3.50", 800), passer("4.00", 900));
        UserSpec noSpec = UserSpec.builder().certifications(new String[]{}).build();

        SpecPositionResult result = calculator.calculate(noSpec, job, () -> null);

        AxisPosition gpa = axis(result, "GPA");
        assertThat(gpa.getPercentile()).isNull();
        assertThat(gpa.getMyValue()).isEqualTo("미입력");
        assertThat(gpa.getMedianValue()).isEqualTo("3.50/4.5"); // 합격자 정보는 그대로 제공
    }

    @Test
    void 합격자_데이터가_없는_축은_행_자체를_만들지_않는다() {
        // v8 규칙 계승: 비교 근거(합격자 데이터)가 없으면 사용자 입력 여부와 무관하게 축을 감춘다.
        PasserData noLang1 = PasserData.builder()
                .gpa(new BigDecimal("3.50")).gpaMax(new BigDecimal("4.50")).build();
        PasserData noLang2 = PasserData.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50")).build();
        PasserData noLang3 = PasserData.builder()
                .gpa(new BigDecimal("4.00")).gpaMax(new BigDecimal("4.50")).build();
        JobSpecProfile job = profileOf("BACKEND", List.of(noLang1, noLang2, noLang3));

        SpecPositionResult result = calculator.calculate(user("3.80", 850), job, () -> null);

        assertThat(result.getAxes()).extracting(AxisPosition::getAxis)
                .contains("GPA")
                .doesNotContain("LANGUAGE");
    }

    @Test
    void 자격증_percentile은_프로필에_등장하는_자격증만_센다() {
        // 정크 입력은 프로필에 없으므로 개수에 못 들어간다 — 예전 v3 불변식과 같은 효과.
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700, "정보처리기사"), passer("3.50", 800, "SQLD"), passer("4.00", 900));

        UserSpec junkUser = user("3.50", 800, "완전정크자격증", "이상한문자열");
        UserSpec certUser = user("3.50", 800, "정보처리기사 필기"); // 표기 변형도 매칭

        assertThat(axis(calculator.calculate(junkUser, job, () -> null), "CERTIFICATION").getMyValue())
                .isEqualTo("0개");
        assertThat(axis(calculator.calculate(certUser, job, () -> null), "CERTIFICATION").getMyValue())
                .isEqualTo("1개");
        assertThat(calculator.calculate(junkUser, job, () -> null).getUnmatchedCertifications())
                .containsExactly("완전정크자격증", "이상한문자열");
    }

    @Test
    void 경험_축은_사용자_입력이_없어도_합격자_중앙값을_보여준다() {
        JobSpecProfile job = profileOf("BACKEND", List.of(
                passerWithExp("3.00", 2), passerWithExp("3.50", 3), passerWithExp("4.00", 5)));

        SpecPositionResult result = calculator.calculate(user("3.50", 800), job, () -> null);

        AxisPosition exp = axis(result, "EXPERIENCE");
        assertThat(exp.getMyValue()).isEqualTo("미입력");
        assertThat(exp.getPercentile()).isNull();
        assertThat(exp.getMedianValue()).isEqualTo("3.0개");
    }

    // --- 갭 리스트 ---

    @Test
    void 갭은_보유율_임계_이상이면서_사용자가_없는_자격증만_보유율_내림차순으로_담는다() {
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700, "정보처리기사", "SQLD"),
                passer("3.50", 800, "정보처리기사"),
                passer("3.60", 820, "정보처리기사", "SQLD"),
                passer("4.00", 900, "ADsP"));

        // 사용자는 SQLD 보유(별칭 표기) → 갭은 정보처리기사(75%), ADsP(25%)만.
        SpecPositionResult result = calculator.calculate(user("3.50", 800, "SQL개발자"), job, () -> null);

        assertThat(result.getGaps()).extracting(SpecGap::getName)
                .containsExactly("정보처리기사", "ADsP");
        assertThat(result.getGaps().get(0).getHolderRatePercent()).isEqualTo(75);
        assertThat(result.getMatchedCertifications()).containsExactly("SQLD");
    }

    @Test
    void 보유율이_임계_미만인_자격증은_갭에_들어가지_않는다() {
        // 6명 중 1명(17%)만 가진 자격증은 "다수가 보유"라 말할 수 없다.
        JobSpecProfile job = profile("BACKEND",
                passer("3.00", 700, "웹디자인기능사"), passer("3.10", 710), passer("3.20", 720),
                passer("3.30", 730), passer("3.40", 740), passer("3.50", 750));

        SpecPositionResult result = calculator.calculate(user("3.50", 800), job, () -> null);

        assertThat(result.getGaps()).isEmpty();
    }

    // --- 헬퍼 ---

    private AxisPosition axis(SpecPositionResult result, String axis) {
        return result.getAxes().stream()
                .filter(a -> a.getAxis().equals(axis))
                .findFirst()
                .orElseThrow(() -> new AssertionError(axis + " 축이 없습니다: " + result.getAxes()));
    }

    private JobSpecProfile profile(String jobType, PasserData... passers) {
        return profileBuilder.build(jobType, List.of(passers));
    }

    private JobSpecProfile profileOf(String jobType, List<PasserData> passers) {
        return profileBuilder.build(jobType, passers);
    }

    private PasserData passer(String gpa, int toeic, String... certs) {
        return PasserData.builder()
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", toeic)))
                .certifications(certs)
                .build();
    }

    private PasserData passerWithExp(String gpa, int expCount) {
        return PasserData.builder()
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .experienceCount(expCount)
                .build();
    }

    private UserSpec user(String gpa, int toeic, String... certs) {
        return UserSpec.builder()
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", toeic)))
                .certifications(certs)
                .build();
    }
}
