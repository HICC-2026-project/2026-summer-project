package com.career.recommendation.util;

import com.career.recommendation.dto.recommendation.CompareRowDto;
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

    // --- 비교탭(CompareRow) 평균 계산 ---

    @Test
    void 학점_결측_합격자는_비교탭_평균에서_제외된다() {
        // 합격자 3명 중 1명이 학점 데이터가 없으면(GPA=null), 그 합격자를 평균에 0으로
        // 끼워 넣지 않고 아예 제외해야 한다. 안 그러면 결측 1명만으로 평균이 크게 왜곡된다
        // (예: 4.00, 4.00, null → 잘못된 방식이면 avg 2.67, 올바른 방식이면 avg 4.00).
        UserSpec user = userSpec("4.00", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData normal1 = passer("4.00", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData normal2 = passer("4.00", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData missingGpa = PasserData.builder()
                .gpa(null).gpaMax(null)
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 900)))
                .certifications(new String[]{"정보처리기사"})
                .build();

        List<CompareRowDto> rows = calculator.calculate(user, List.of(normal1, normal2, missingGpa)).getCompareRows();
        CompareRowDto gpaRow = rows.get(0);

        assertThat(gpaRow.getAvgVal()).isEqualTo("4.00/4.5");
        assertThat(gpaRow.getAvgPct()).isEqualTo(89);
    }

    @Test
    void 어학_결측_합격자는_비교탭_평균에서_제외된다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData normal1 = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData normal2 = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData missingLang = PasserData.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of())
                .certifications(new String[]{"정보처리기사"})
                .build();

        List<CompareRowDto> rows = calculator.calculate(user, List.of(normal1, normal2, missingLang)).getCompareRows();
        CompareRowDto langRow = rows.get(1);

        assertThat(langRow.getAvgVal()).isEqualTo("환산 900");
    }

    @Test
    void 자격증_0개인_합격자는_평균_계산에서_제외되지_않는다() {
        // GPA·어학과 달리 자격증 0개는 "결측"이 아니라 "실제로 안 가짐"이므로,
        // 그 합격자를 평균에서 빼면 안 된다 — 0으로 포함시키는 게 맞다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData withCert = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData withoutCert = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        List<CompareRowDto> rows = calculator.calculate(user, List.of(withCert, withoutCert)).getCompareRows();
        CompareRowDto certRow = rows.get(2);

        // 정보처리기사(2.0) + 0 을 2명으로 나눈 평균 개수는 0.5개여야 한다(제외되면 1.0개가 됨).
        assertThat(certRow.getAvgVal()).isEqualTo("0.5개");
    }

    // --- 자격증 가중치 매칭 (v3 — 인식된 자격증만 점수가 된다) ---

    @Test
    void 자격증_표기가_달라도_정규화되면_만점을_받는다() {
        // "정보처리기사 필기"는 canonicalCert를 거치면 "정보처리기사"와 같아져야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사 필기"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 전각공백이_섞여도_정규화된다() {
        // 노션·한글 문서·일부 모바일 키보드에서 흔한 전각공백(U+3000, "　")은
        // 정규식 \s가 ASCII 공백만 매칭해 놓치기 쉽다. 놓치면 "SQLD　필기"가
        // "SQLD　"로 남아 CERT_WEIGHTS의 "SQLD"와 안 맞고 조용히 기본 가중치로 떨어진다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD　필기"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

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
    void 완전히_미인식된_자격증은_미입력과_점수가_같다() {
        // v3의 핵심 목표: 세 층(CERT_WEIGHTS·합격자DB·국가기술자격) 어디에도 없는 정크 문자열은
        // "인정 안 됨"이 아니라 정확히 0으로 처리되어, 미입력과 동률이어야 한다.
        // (v2까지는 기본 가중치가 0보다 커서, 정크를 아무리 상한 안에서 적어도 미입력보다는
        // 항상 유리했다 — "정크가 미입력보다 낫다"는 유인을 구조적으로 없애는 게 이번 재설계의 목적.)
        UserSpec junkCerts = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"가나다", "라마바", "사아자", "차카타", "파하가"});
        UserSpec noCerts = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        int scoreWithJunk = calculator.calculate(junkCerts, List.of(passer)).getTotalScore();
        int scoreWithNone = calculator.calculate(noCerts, List.of(passer)).getTotalScore();

        assertThat(scoreWithJunk).isEqualTo(scoreWithNone);
    }

    @Test
    void 합격자_DB에_실제_등장한_자격증은_표에_없어도_인식된다() {
        // 2층: CERT_WEIGHTS에 없는 자격증이라도, 같은 후보 풀의 다른 합격자가 실제로
        // 보유하고 있으면 "실재가 검증됐다"고 보고 인정한다.
        PasserData rareCertHolder = passer("3.80", "4.50", "IH", 900, new String[]{"레어자격증"}, 1);
        PasserData standardHolder = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        List<PasserData> pool = List.of(rareCertHolder, standardHolder);

        UserSpec withRareCert = userSpec("3.80", "4.50", "IH", 900, new String[]{"레어자격증"});
        UserSpec withNoCert = userSpec("3.80", "4.50", "IH", 900, new String[]{});

        assertThat(calculator.calculate(withRareCert, pool).getTotalScore())
                .isGreaterThan(calculator.calculate(withNoCert, pool).getTotalScore());
    }

    @Test
    void 국가기술자격_종목은_합격자_DB에_없어도_인식된다() {
        // 3층: NATIONAL_TECH_CERTIFICATIONS에 있는 공식 종목명은 이번 비교의 합격자 풀에
        // 그 자격증을 가진 사람이 전혀 없어도 인정되어야 한다.
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        UserSpec withNationalCert = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보통신기사"});
        UserSpec withJunk = userSpec("3.80", "4.50", "IH", 900, new String[]{"아무말이나적음"});

        assertThat(calculator.calculate(withNationalCert, List.of(passer)).getTotalScore())
                .isGreaterThan(calculator.calculate(withJunk, List.of(passer)).getTotalScore());
    }

    @Test
    void 미인식_자격증은_원본_표기_그대로_결과에_보고된다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"정보처리기사", "출처불명자격증"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        List<String> unrecognized = calculator.calculate(user, List.of(passer)).getUnrecognizedCertifications();

        assertThat(unrecognized).containsExactly("출처불명자격증");
    }

    @Test
    void 합격자가_자격증이_없으면_사용자_자격증과_무관하게_만점을_받는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        assertThat(calculator.calculate(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 운영_DB_자격증_9종은_전부_CERT_WEIGHTS에_정확히_매핑된다() throws Exception {
        // CERT_WEIGHTS의 key가 canonicalCert()의 출력과 어긋나면 예외 없이 조용히
        // 미인식(0점) 처리된다(2층·3층에서 우연히 구제되지 않는 한). 운영 DB 실제 표기 9종이
        // 전부 CERT_WEIGHTS에 정확히 등록되어 있는지 여기서 확인한다 —
        // 이 값이 어긋나면 이 테스트가 먼저 깨져야 한다.
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
