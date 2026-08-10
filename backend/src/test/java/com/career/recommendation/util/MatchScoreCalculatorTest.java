package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    // --- 자격증 가중치 매칭 (v2) ---

    @Test
    void 자격증_표기가_달라도_정규화되면_만점을_받는다() {
        // "정보처리기사 필기"는 canonicalCert를 거치면 "정보처리기사"와 같아져야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사 필기"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 자격증_별칭도_표준_표기와_동일하게_매칭된다() {
        // "SQL개발자"는 CERT_ALIASES를 통해 "SQLD"로 모여야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQL개발자"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 등록되지_않은_자격증도_기본_가중치로_일부_인정된다() {
        // 예전엔 완전 일치가 아니면 0점이었다. 표에 없는 자격증도 기본 가중치(0.5)로
        // 일부는 인정되어야 한다 — 단, 합격자(2.0)보다는 낮은 점수여야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"이상한자격증"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        int totalScore = calculator.calculate(user, List.of(passer)).getTotalScore();
        assertThat(totalScore).isGreaterThan(0);
        // Cert = 0.5/2.0*100 = 25 → Total = 40 + 33.3 + 25*4/15 ≈ 80
        assertThat(totalScore).isLessThan(100);
    }

    @Test
    void 미등록_자격증을_여러개_적어도_상한_이상은_점수가_오르지_않는다() {
        // 게이밍 방지: MAX_DEFAULT_WEIGHT_CERTS(2)개를 넘는 미등록 자격증은 더 이상 반영되지 않는다.
        UserSpec twoJunk = userSpec("3.80", "4.50", "IH", 900, new String[]{"가나다", "라마바"});
        UserSpec sixJunk = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"가나다", "라마바", "사아자", "차카타", "파하가", "나다라"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        int scoreWithTwo = calculator.calculate(twoJunk, List.of(passer)).getTotalScore();
        int scoreWithSix = calculator.calculate(sixJunk, List.of(passer)).getTotalScore();

        assertThat(scoreWithSix).isEqualTo(scoreWithTwo);
        assertThat(scoreWithSix).isLessThan(100);
    }

    @Test
    void 합격자가_자격증이_없으면_사용자_자격증과_무관하게_만점을_받는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 운영_DB_자격증_9종은_전부_기본_가중치로_떨어지지_않는다() throws Exception {
        // CERT_WEIGHTS의 key가 canonicalCert()의 출력과 어긋나면 예외 없이 조용히
        // DEFAULT_CERT_WEIGHT(0.5)로 떨어진다. 운영 DB 실제 표기 9종이 전부 등록된
        // 가중치를 받는지 여기서 확인한다 — 이 값이 어긋나면 이 테스트가 먼저 깨져야 한다.
        String[] productionCerts = {
                "SQLD", "정보처리기사", "ADsP", "빅데이터분석기사", "AWS SAA",
                "리눅스마스터 2급", "정보보안기사", "네트워크관리사 2급", "웹디자인기능사"
        };

        Method canonicalCert = MatchScoreCalculator.class.getDeclaredMethod("canonicalCert", String.class);
        canonicalCert.setAccessible(true);

        Field weightsField = MatchScoreCalculator.class.getDeclaredField("CERT_WEIGHTS");
        weightsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> weights = (Map<String, Double>) weightsField.get(null);

        for (String raw : productionCerts) {
            String canonical = (String) canonicalCert.invoke(calculator, raw);
            assertThat(weights)
                    .as("'%s' → '%s' 가 CERT_WEIGHTS에 등록되어 있어야 한다", raw, canonical)
                    .containsKey(canonical);
        }
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
