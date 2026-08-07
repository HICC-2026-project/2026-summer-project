package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScoreCalculatorTest {

    private final MatchScoreCalculator calculator = new MatchScoreCalculator();

    @Test
    void 동일한_스펙이면_100점을_반환한다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 서로_다른_학점_만점은_비율로_비교한다() {
        UserSpec user = userSpec("4.00", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.55", "4.00", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 토익과_OPIC은_각_시험의_동일한_유형끼리_비교한다() {
        // user: OPIC IH = 900, TOEIC 800 -> max = 900
        // passer: OPIC AL = 950, TOEIC 900 -> max = 950
        // Lang = 900 / 950 * 100 = 94.73
        // Total = 40 (GPA) + 26.67 (Cert) + 31.58 (Lang) = 98.25 -> 98
        UserSpec user = userSpec("3.80", "4.50", "IH", 800, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "AL", 900, new String[]{"SQLD"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(98);
    }

    @Test
    void 서로_다른_시험이라도_환산_점수로_비교한다() {
        // user: OPIC IM2 -> 750
        // passer: TOEIC 800
        // Lang = 750 / 800 * 100 = 93.75
        // Total = 40 + 26.67 + 31.25 = 97.92 -> 98
        UserSpec user = UserSpec.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "OPIC", "grade", "IM2")))
                .certifications(new String[]{"SQLD"}).build();
        
        PasserData passer = PasserData.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 800)))
                .certifications(new String[]{"SQLD"}).build();

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(98);
    }

    @Test
    void TOEFL_점수를_선형보간으로_환산하여_비교한다() {
        // user: TOEFL 81 -> (77~86 구간 -> 750~800) -> 750 + (81-77)*50/9 = 772.22
        // passer: TOEIC 800
        // Lang = 772.22 / 800 * 100 = 96.52
        // Total = 40 + 26.67 + 32.17 = 98.84 -> 99
        UserSpec user = UserSpec.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEFL", "score", 81)))
                .certifications(new String[]{"SQLD"}).build();

        PasserData passer = PasserData.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 800)))
                .certifications(new String[]{"SQLD"}).build();

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(99);
    }

    @Test
    void 경험수는_matchScore에_영향을_주지_않는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData noExperience = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 0);
        PasserData manyExperiences = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 100);

        assertThat(calculator.calculate(user, List.of(noExperience)).getTotalScore())
                .isEqualTo(calculator.calculate(user, List.of(manyExperiences)).getTotalScore());
    }

    @Test
    void 사용자_스펙이_없으면_0점을_반환한다() {
        assertThat(calculator.calculate(null, List.of(PasserData.builder().build())).getTotalScore()).isZero();
    }

    private UserSpec userSpec(String gpa, String gpaMax, String opicGrade,
                              int toeicScore, String[] certifications) {
        return UserSpec.builder()
                .gpa(new BigDecimal(gpa))
                .gpaMax(new BigDecimal(gpaMax))
                .languageScores(languageScores(opicGrade, toeicScore))
                .certifications(certifications)
                .build();
    }

    private PasserData passer(String gpa, String gpaMax, String opicGrade,
                              int toeicScore, String[] certifications, int experienceCount) {
        return PasserData.builder()
                .gpa(new BigDecimal(gpa))
                .gpaMax(new BigDecimal(gpaMax))
                .languageScores(languageScores(opicGrade, toeicScore))
                .certifications(certifications)
                .experienceCount(experienceCount)
                .build();
    }

    private List<Map<String, Object>> languageScores(String opicGrade, int toeicScore) {
        return List.of(
                Map.of("type", "TOEIC", "score", toeicScore, "maxScore", 990),
                Map.of("type", "OPIC", "grade", opicGrade)
        );
    }
}
