package com.career.recommendation.util;

import com.career.recommendation.dto.recommendation.CompareRowDto;
import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScoreCalculatorTest {

    private final MatchScoreCalculator calculator = new MatchScoreCalculator();

    /**
     * 대부분의 테스트는 2층(합격자 DB 실관측) 인식이 필요 없어 빈 풀로 충분하다.
     * 직무 자격증 행도 비워 두면 기존 큐레이션 표(CERT_WEIGHTS)로 폴백하므로,
     * v6 이전에 작성된 테스트의 기대값이 그대로 유지된다.
     */
    private MatchScoreResult calc(UserSpec user, List<PasserData> passers) {
        return calculator.calculate(user, passers, Set.of(), List.of());
    }

    /** 직무별 가중치를 검증할 때 쓰는 오버로드. jobRows는 해당 직무 합격자 1인 1행. */
    private MatchScoreResult calc(UserSpec user, List<PasserData> passers, List<String[]> jobRows) {
        return calculator.calculate(user, passers, Set.of(), jobRows);
    }

    @Test
    void 동일한_스펙이면_100점을_반환한다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 서로_다른_학점_만점은_비율로_비교한다() {
        UserSpec user = userSpec("4.00", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.55", "4.00", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 토익과_OPIC은_각_시험의_동일한_유형끼리_비교한다() {
        // user: OPIC IH = 900, TOEIC 800 -> max = 900
        // passer: OPIC AL = 950, TOEIC 900 -> max = 950
        // Lang = 900 / 950 * 100 = 94.73
        // Total = 40 (GPA) + 26.67 (Cert) + 31.58 (Lang) = 98.25 -> 98
        UserSpec user = userSpec("3.80", "4.50", "IH", 800, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "AL", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(98);
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

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(98);
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

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(99);
    }

    @Test
    void 경험수는_matchScore에_영향을_주지_않는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData noExperience = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 0);
        PasserData manyExperiences = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 100);

        assertThat(calc(user, List.of(noExperience)).getTotalScore())
                .isEqualTo(calc(user, List.of(manyExperiences)).getTotalScore());
    }

    @Test
    void 사용자_스펙이_없으면_0점을_반환한다() {
        assertThat(calc(null, List.of(PasserData.builder().build())).getTotalScore()).isZero();
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

        List<CompareRowDto> rows = calc(user, List.of(normal1, normal2, missingGpa)).getCompareRows();
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

        List<CompareRowDto> rows = calc(user, List.of(normal1, normal2, missingLang)).getCompareRows();
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

        List<CompareRowDto> rows = calc(user, List.of(withCert, withoutCert)).getCompareRows();
        CompareRowDto certRow = rows.get(2);

        // 정보처리기사(2.0) + 0 을 2명으로 나눈 평균 값은 0.5여야 한다(제외되면 1.0이 됨).
        // 표시는 "인식된 개수" 기준(둘 다 인식되므로 1명은 1개, 1명은 0개 → 평균 0.5개).
        assertThat(certRow.getAvgVal()).isEqualTo("0.5개");
    }

    @Test
    void 합격자_전원이_자격증_0개면_막대는_0이고_상태는_충족이다() {
        // 막대는 "합격자 대비"가 아니라 "절대 수준"이므로, 양쪽 다 자격증이 없으면 0%가 맞다.
        // 감점되지 않는다는 사실은 status("충족")와 총점이 전달한다.
        // (예전엔 상대 기준이라 0/0을 100%로 예외 처리했는데, 자격증이 없는 사용자에게
        //  "만점"이라고 표시하는 셈이라 오해를 만들었다.)
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        CompareRowDto certRow = calc(user, List.of(passer)).getCompareRows().get(2);

        assertThat(certRow.getMyPct()).isZero();
        assertThat(certRow.getAvgPct()).isZero();
        assertThat(certRow.getStatus()).isEqualTo("충족");
    }

    @Test
    void 자격증_막대는_합격자_구성이_바뀌면_함께_움직인다() {
        // 예전엔 합격자 평균 자신으로 나눠서(avg / (avg * 1.5)) 합격자 막대가 데이터와
        // 무관하게 항상 67%로 고정됐다 — 데이터처럼 보이지만 정보가 없는 막대였다.
        // 고정 기준으로 나누는 지금은 합격자 자격증이 늘면 막대도 함께 올라가야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});

        PasserData light = passer("3.80", "4.50", "IH", 900, new String[]{"웹디자인기능사"}, 1);       // 0.5
        PasserData heavy = passer("3.80", "4.50", "IH", 900,
                new String[]{"정보처리기사", "SQLD", "ADsP"}, 1);                                      // 4.5

        int lightAvgPct = calc(user, List.of(light)).getCompareRows().get(2).getAvgPct();
        int heavyAvgPct = calc(user, List.of(heavy)).getCompareRows().get(2).getAvgPct();

        assertThat(lightAvgPct).isLessThan(heavyAvgPct);
        // 고정 기준(4.5) 기준의 실제 값까지 고정해 둔다 — 0.5/4.5 = 11%, 4.5/4.5 = 100%.
        assertThat(lightAvgPct).isEqualTo(11);
        assertThat(heavyAvgPct).isEqualTo(100);
    }

    @Test
    void 자격증_막대는_사용자_가중치에_비례한다() {
        // 정보처리기사(2.0) / 4.5 = 44%
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        CompareRowDto certRow = calc(user, List.of(passer)).getCompareRows().get(2);

        assertThat(certRow.getMyPct()).isEqualTo(44);
        assertThat(certRow.getAvgPct()).isEqualTo(33); // SQLD(1.5) / 4.5
    }

    // --- 자격증 가중치 매칭 (v3 — 인식된 자격증만 점수가 된다) ---

    @Test
    void 자격증_표기가_달라도_정규화되면_만점을_받는다() {
        // "정보처리기사 필기"는 canonicalCert를 거치면 "정보처리기사"와 같아져야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사 필기"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 전각공백이_섞여도_정규화된다() {
        // 노션·한글 문서·일부 모바일 키보드에서 흔한 전각공백(U+3000, "　")은
        // 정규식 \s가 ASCII 공백만 매칭해 놓치기 쉽다. 놓치면 "SQLD　필기"가
        // "SQLD　"로 남아 CERT_WEIGHTS의 "SQLD"와 안 맞고 조용히 미인식 처리된다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD　필기"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 자격증_별칭도_표준_표기와_동일하게_매칭된다() {
        // "SQL개발자"는 CERT_ALIASES를 통해 "SQLD"로 모여야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQL개발자"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
    }

    @Test
    void 완전히_미인식된_자격증은_미입력과_점수가_같다() {
        // v3의 핵심 목표: 세 층(CERT_WEIGHTS·합격자DB·국가기술자격) 어디에도 없는 정크 문자열은
        // "인정 안 됨"이 아니라 정확히 0으로 처리되어, 미입력과 동률이어야 한다.
        UserSpec junkCerts = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"가나다", "라마바", "사아자", "차카타", "파하가"});
        UserSpec noCerts = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        int scoreWithJunk = calc(junkCerts, List.of(passer)).getTotalScore();
        int scoreWithNone = calc(noCerts, List.of(passer)).getTotalScore();

        assertThat(scoreWithJunk).isEqualTo(scoreWithNone);
    }

    @Test
    void 국가기술자격_종목은_합격자_DB에_없어도_인식된다() {
        // 3층: NATIONAL_TECH_CERTIFICATIONS에 있는 공식 종목명은 이번 비교의 합격자 풀에
        // 그 자격증을 가진 사람이 전혀 없어도 인정되어야 한다.
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        UserSpec withNationalCert = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보통신기사"});
        UserSpec withJunk = userSpec("3.80", "4.50", "IH", 900, new String[]{"아무말이나적음"});

        assertThat(calc(withNationalCert, List.of(passer)).getTotalScore())
                .isGreaterThan(calc(withJunk, List.of(passer)).getTotalScore());
    }

    @Test
    void 합격자가_자격증이_없으면_사용자_자격증과_무관하게_만점을_받는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(100);
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

    // --- 1번: 2층 인식은 전역 자격증 풀(globalCertPool) 기준이어야 한다 ---

    @Test
    void 레이어2_인식은_비교대상_합격자가_아니라_전역_풀_기준이다() {
        // 예전엔 2층(observedCerts)을 "이번에 비교하는 합격자 목록(passerList)"에서만
        // 뽑았다. 그러면 유사 합격자 Top N 구성이 바뀔 때마다(스펙 미세 수정, 데이터 추가 등)
        // 같은 자격증의 인식 여부가 흔들렸다. 지금은 globalCertPool을 별도로 주입받아,
        // 비교 대상 passerList에 그 자격증을 가진 사람이 "아무도 없어도" 전역 풀에만
        // 있으면 인식돼야 한다.
        PasserData passerWithoutRareCert = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        UserSpec userWithRareCert = userSpec("3.80", "4.50", "IH", 900, new String[]{"레어자격증"});

        MatchScoreResult withPoolContainingIt = calculator.calculate(
                userWithRareCert, List.of(passerWithoutRareCert), Set.of("레어자격증"), List.of());
        MatchScoreResult withPoolNotContainingIt = calculator.calculate(
                userWithRareCert, List.of(passerWithoutRareCert), Set.of(), List.of());

        assertThat(withPoolContainingIt.getTotalScore()).isGreaterThan(withPoolNotContainingIt.getTotalScore());
        assertThat(withPoolContainingIt.getUnrecognizedCertifications()).isEmpty();
        assertThat(withPoolNotContainingIt.getUnrecognizedCertifications()).containsExactly("레어자격증");
    }

    @Test
    void 레이어2_인식은_비교대상_합격자_구성이_바뀌어도_동일하다() {
        // 위 테스트의 재확인: 같은 globalCertPool을 유지하는 한, passerList(비교 대상)의
        // 구성이 달라져도(누가 Top N에 뽑히든) 자격증 인식 여부와 점수는 흔들리지 않아야 한다.
        Set<String> pool = Set.of("레어자격증");
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"레어자격증"});

        PasserData poolA = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);
        PasserData poolB = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        int scoreA = calculator.calculate(user, List.of(poolA), pool, List.of()).getTotalScore();
        int scoreB = calculator.calculate(user, List.of(poolB), pool, List.of()).getTotalScore();

        // 비교 대상 자체가 다르므로(정보처리기사 2.0 vs SQLD 1.5) 총점은 다를 수 있지만,
        // '레어자격증'이 미인식으로 떨어지는 일은 없어야 한다.
        assertThat(calculator.calculate(user, List.of(poolA), pool, List.of()).getUnrecognizedCertifications()).isEmpty();
        assertThat(calculator.calculate(user, List.of(poolB), pool, List.of()).getUnrecognizedCertifications()).isEmpty();
    }

    // --- 2번: myVal·avgVal은 "입력 개수"가 아니라 "인식된 개수" ---

    @Test
    void myVal은_입력개수가_아니라_인식된_개수를_보여준다() {
        // 정크 5개를 입력하면 화면엔 "5개"가 아니라 "0개"가 떠야 한다.
        // 원본 5개는 unrecognizedCertifications로 별도 보고된다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"정크1", "정크2", "정크3", "정크4", "정크5"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        MatchScoreResult result = calc(user, List.of(passer));
        CompareRowDto certRow = result.getCompareRows().get(2);

        assertThat(certRow.getMyVal()).isEqualTo("0개");
        assertThat(result.getUnrecognizedCertifications()).hasSize(5);
    }

    @Test
    void myVal은_인식된_것과_미인식된_것이_섞이면_인식된_것만_센다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"정보처리기사", "SQLD", "정크"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        CompareRowDto certRow = calc(user, List.of(passer)).getCompareRows().get(2);

        assertThat(certRow.getMyVal()).isEqualTo("2개");
    }

    // --- 3번: 미인식 목록은 정규화 기준으로 중복 제거된다 ---

    @Test
    void 미인식_목록은_정규화하면_같아지는_표기를_한_번만_보여준다() {
        // "정크자격", "정크자격 "(공백), "정크자격()"(괄호)는 canonicalCert를 거치면 전부
        // 같은 값이다. 원본 문자열 기준으로만 distinct하면 눈에는 같아 보이는 값이
        // 배너에 세 번 나열되는 문제가 있었다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"정크자격", "정크자격 ", "정크자격()"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        List<String> unrecognized = calc(user, List.of(passer)).getUnrecognizedCertifications();

        assertThat(unrecognized).hasSize(1);
        assertThat(unrecognized.get(0)).isEqualTo("정크자격"); // 맨 처음 등장한 원본 표기를 보여준다
    }

    @Test
    void 미인식_목록은_서로_다른_자격증이면_전부_보여준다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900,
                new String[]{"정크A", "정크B", "정크A"}); // 정크A는 원본까지 완전히 동일 중복
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        List<String> unrecognized = calc(user, List.of(passer)).getUnrecognizedCertifications();

        assertThat(unrecognized).containsExactly("정크A", "정크B");
    }

    // --- 4번: 유사 합격자가 0명이어도 미인식 고지는 살아있어야 한다 ---

    @Test
    void 유사_합격자가_0명이어도_미인식_자격증은_보고된다() {
        // 예전엔 passerList가 비어있으면 total/compareRows뿐 아니라
        // unrecognizedCertifications까지 통째로 빈 리스트를 반환했다. 자격증 인식 여부는
        // 유사 합격자 유무와 무관한 질문이라, 총점을 못 내는 상황에서도 "이 자격증은
        // 우리가 못 알아봤다"는 사실만큼은 알려줄 수 있어야 한다.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"완전정크자격증"});

        MatchScoreResult result = calculator.calculate(user, List.of(), Set.of(), List.of());

        assertThat(result.getTotalScore()).isZero();
        assertThat(result.getUnrecognizedCertifications()).containsExactly("완전정크자격증");
    }

    @Test
    void 유사_합격자가_0명이면_인식된_자격증에_대해선_미인식_목록이_비어있다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});

        MatchScoreResult result = calculator.calculate(user, List.of(), Set.of(), List.of());

        assertThat(result.getTotalScore()).isZero(); // 비교 대상이 없어 총점은 0
        assertThat(result.getUnrecognizedCertifications()).isEmpty(); // 하지만 인식은 됨
    }

    // --- 직무별 자격증 가중치 (v6) ---

    @Test
    void 자격증_가중치는_직무_합격자_보유율에서_유도된다() {
        // 합격자 4명 중 웹디자인기능사 3명(75%), SQLD 1명(25%).
        // 큐레이션 표에서는 웹디자인기능사(0.5) < SQLD(1.5)지만,
        // 이 직무에서는 보유율이 뒤집혀 있으므로 가중치도 뒤집혀야 한다.
        List<String[]> jobRows = List.of(
                new String[]{"웹디자인기능사"},
                new String[]{"웹디자인기능사"},
                new String[]{"웹디자인기능사", "SQLD"},
                new String[]{});

        UserSpec webUser = userSpec("3.80", "4.50", "IH", 900, new String[]{"웹디자인기능사"});
        UserSpec sqldUser = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"웹디자인기능사"}, 1);

        int webScore = calc(webUser, List.of(passer), jobRows).getTotalScore();
        int sqldScore = calc(sqldUser, List.of(passer), jobRows).getTotalScore();

        assertThat(webScore).isGreaterThan(sqldScore);

        // 위 방향성 단언만으로는 helper 함수 자체가 프로덕션 코드를 실제로 거치는지
        // 증명하지 못한다(계산식을 테스트 안에서 재구현해 비교하면, 상수를 바꿔도
        // 항상 자기 자신과만 일치해 통과해버린다). 실제 CompareRowDto 값으로 고정한다.
        // 웹디자인기능사 보유율 75% → weight 2.0 → 2.0/4.5(CERT_SCALE_MAX) = 44%
        // SQLD 보유율 25% → weight 1.0 → 1.0/4.5 = 22%
        int webPct = calc(webUser, List.of(passer), jobRows).getCompareRows().get(2).getMyPct();
        int sqldPct = calc(sqldUser, List.of(passer), jobRows).getCompareRows().get(2).getMyPct();
        assertThat(webPct).isEqualTo(44);
        assertThat(sqldPct).isEqualTo(22);
    }

    @Test
    void 자격증이_없는_합격자도_보유율_분모에_포함된다() {
        // 자격증을 가진 사람만 분모로 쓰면 보유율이 부풀려진다.
        // 4명 중 1명 보유 = 25%여야 하고, "자격증 보유자 1명 중 1명 = 100%"가 되면 안 된다.
        // List.of()는 null 원소를 거부하므로(자격증 없는 합격자 행을 null로 표현하려면)
        // Arrays.asList를 쓴다.
        List<String[]> jobRows = Arrays.asList(
                new String[]{"SQLD"}, null, new String[]{}, null);

        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        // 합격자도 같은 자격증 하나만 갖게 해 사용자/합격자 가중치가 동일해지도록 한다.
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        CompareRowDto certRow = calc(user, List.of(passer), jobRows).getCompareRows().get(2);

        // 보유율 25% → 가중치 1.0 → 막대는 1.0 / CERT_SCALE_MAX(4.5) = 22%
        // 보유율을 100%로 잘못 계산하면 가중치 2.5 → 56%가 되어 이 단언이 깨진다.
        assertThat(certRow.getMyPct()).isEqualTo(22);
    }

    @Test
    void 직무_데이터가_없으면_기존_큐레이션_표로_폴백한다() {
        // 정보처리기사는 표에서 2.0. 막대는 2.0 / 4.5 = 44%.
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        CompareRowDto certRow = calc(user, List.of(passer), List.of()).getCompareRows().get(2);

        assertThat(certRow.getMyPct()).isEqualTo(44);
    }

    @Test
    void 직무_합격자는_있지만_전원_자격증이_0개면_큐레이션_표로_폴백하지_않는다() {
        // "직무 데이터가 아예 없음"과 "직무 합격자는 있는데 전원 자격증 0개"는 다르다.
        // buildJobCertWeights()가 둘 다 빈 맵을 만들어서, jobWeights.isEmpty()로만 폴백을
        // 판단하면 후자도 CERT_WEIGHTS(정보처리기사=2.0)로 잘못 빠진다.
        // 실데이터가 "이 직무는 자격증 보유율 0%"라고 말하는데, 그걸 무시하고 임의 판단표를
        // 쓰게 되면 애초에 보유율 기반 가중치를 도입한 이유가 이 케이스에서 무효화된다.
        List<String[]> jobRowsAllEmpty = Arrays.asList(null, null, new String[]{}, null);

        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{}, 1);

        CompareRowDto certRow = calc(user, List.of(passer), jobRowsAllEmpty).getCompareRows().get(2);

        // 하한(0.5) / 4.5 = 11%여야 한다. CERT_WEIGHTS로 폴백하면 2.0/4.5 = 44%가 나온다.
        assertThat(certRow.getMyPct()).isEqualTo(11);
    }

    @Test
    void 그_직무_합격자가_아무도_안_가진_자격증도_인식되면_하한_가중치를_받는다() {
        // SQLD는 이 직무 합격자 보유율 0%지만 실재하는 자격증이므로 0점이 아니라 하한(0.5)이다.
        // 0점은 "우리가 알아보지 못한 입력"에만 주는 값이라는 v3 불변식을 지켜야 한다.
        List<String[]> jobRows = List.of(new String[]{"정보처리기사"}, new String[]{"정보처리기사"});

        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        MatchScoreResult result = calc(user, List.of(passer), jobRows);

        // 0.5 / 4.5 = 11%. 미인식으로 처리됐다면 0%가 된다.
        assertThat(result.getCompareRows().get(2).getMyPct()).isEqualTo(11);
        assertThat(result.getUnrecognizedCertifications()).isEmpty();
    }

    @Test
    void 직무_가중치가_있어도_미인식_자격증은_여전히_0점이다() {
        // v3부터 지켜온 핵심 불변식. 직무 가중치 도입으로 깨지면 안 된다.
        List<String[]> jobRows = List.of(new String[]{"정보처리기사"}, new String[]{"SQLD"});

        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"완전정크자격증123"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        MatchScoreResult result = calc(user, List.of(passer), jobRows);

        assertThat(result.getCompareRows().get(2).getMyPct()).isZero();
        assertThat(result.getUnrecognizedCertifications()).containsExactly("완전정크자격증123");
    }

    @Test
    void 같은_자격증도_직무가_다르면_다른_가중치를_받는다() {
        UserSpec user = userSpec("3.80", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.80", "4.50", "IH", 900, new String[]{"정보처리기사"}, 1);

        List<String[]> sqldHeavyJob = List.of(new String[]{"SQLD"}, new String[]{"SQLD"});      // 100%
        List<String[]> sqldLightJob = List.of(new String[]{"SQLD"}, new String[]{"정보처리기사"}); // 50%

        int heavy = calc(user, List.of(passer), sqldHeavyJob).getCompareRows().get(2).getMyPct();
        int light = calc(user, List.of(passer), sqldLightJob).getCompareRows().get(2).getMyPct();

        assertThat(heavy).isEqualTo(56); // 2.5 / 4.5
        assertThat(light).isEqualTo(33); // 1.5 / 4.5
    }

    // --- 학점 바닥값 (v5) ---

    @Test
    void 학점이_바닥값_이하면_학점_항목은_0점이다() {
        // 바닥값 0.5 = 만점의 절반. 2.25/4.50이 정확히 경계다.
        // 어학·자격증을 합격자와 동일하게 맞춰 학점 기여분만 남긴다(어학 33.3 + 자격증 26.7 = 60).
        UserSpec user = userSpec("2.20", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.70", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(60);
    }

    @Test
    void 학점_점수는_바닥값부터_합격자까지를_0에서_100으로_편다() {
        // 합격자 0.8222(3.70/4.50), 바닥 0.5 → 구간 0.3222
        // 유저 0.6667(3.00/4.50) → (0.6667-0.5)/0.3222 = 51.7 → 학점 기여 20.7
        // 어학·자격증은 동일하므로 60 + 20.7 = 80.7 → 81
        UserSpec user = userSpec("3.00", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("3.70", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(81);
    }

    @Test
    void 합격자_학점이_바닥값_이하면_단순_비율로_비교한다() {
        // 합격자 0.4444(2.00/4.50)는 바닥값보다 낮아 구간을 만들 수 없다.
        // 이때는 바닥값을 적용하지 않고 예전처럼 단순 비율로 비교한다(분모 0 이하 방지).
        // 유저 0.2222(1.00/4.50) / 합격자 0.4444 = 50 → 학점 기여 20 → 60 + 20 = 80
        UserSpec user = userSpec("1.00", "4.50", "IH", 900, new String[]{"SQLD"});
        PasserData passer = passer("2.00", "4.50", "IH", 900, new String[]{"SQLD"}, 1);

        assertThat(calc(user, List.of(passer)).getTotalScore()).isEqualTo(80);
    }

    @Test
    void 학점이_높고_어학이_없는_쪽이_학점이_낮고_어학이_있는_쪽보다_높다() {
        // v5 이전의 역전 케이스를 고정한다. 학점 축만 하위 구간을 쓰지 않아
        // "3.7·어학 미입력(40점)"이 "2.5·토익 500(47점)"보다 낮게 나왔다.
        List<PasserData> passers = List.of(
                passer("3.70", "4.50", "IH", 850, new String[]{"정보처리기사", "SQLD"}, 1),
                passer("3.60", "4.50", "IH", 820, new String[]{"SQLD"}, 1),
                passer("3.80", "4.50", "IH", 840, new String[]{"정보처리기사", "AWS SAA"}, 1));

        UserSpec highGpaOnly = UserSpec.builder()
                .gpa(new BigDecimal("3.70")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of())
                .certifications(new String[]{})
                .build();
        UserSpec lowGpaWithLang = UserSpec.builder()
                .gpa(new BigDecimal("2.50")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 500, "maxScore", 990)))
                .certifications(new String[]{})
                .build();

        int highGpaScore = calc(highGpaOnly, passers).getTotalScore();
        int lowGpaScore = calc(lowGpaWithLang, passers).getTotalScore();

        assertThat(highGpaScore).isGreaterThan(lowGpaScore);
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
