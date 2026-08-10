package com.career.recommendation.util;

import com.career.recommendation.dto.recommendation.CompareRowDto;
import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — 유저 스펙과 합격자 데이터를 비교하여 MatchScoreResult를 계산한다.
 * 항목별 내역 (학점, 어학, 자격증) 및 총점을 반환한다.
 */
@Component
public class MatchScoreCalculator {

    private static final double WEIGHT_GPA  = 0.40;
    private static final double WEIGHT_LANG = 1.0 / 3.0;
    private static final double WEIGHT_CERT = 4.0 / 15.0;

    /**
     * 점수 산출 공식의 버전. 공식이 바뀌면 반드시 올린다.
     * RecommendationService가 캐시된 응답의 scoreFormulaVersion과 비교해,
     * 옛 버전으로 계산된 캐시는 legacy로 판정하여 다음 요청에서 한 번 강제로 재계산한다.
     * (버전을 안 올리면 배포 후에도 유저마다 옛 공식/새 공식 점수가 뒤섞여 보인다.)
     *
     * 1: 자격증 완전 일치 매칭 (배포 초기 — 자유 텍스트와 궁합이 나빠 대부분 0점)
     * 2: 자격증 이름 정규화 + 가중치 합산 매칭
     */
    public static final int CURRENT_SCORE_FORMULA_VERSION = 2;

    /** 표에 없는 자격증에 부여하는 기본 가중치. */
    private static final double DEFAULT_CERT_WEIGHT = 0.5;

    /** 기본 가중치가 붙는 자격증(=CERT_WEIGHTS에 없는 표기)은 최대 이 개수까지만 인정한다(게이밍 방지). */
    private static final int MAX_DEFAULT_WEIGHT_CERTS = 2;

    /** 공백·구분기호·필기/실기 등 부가어를 지우기 위한 패턴. canonicalCert에서 이 순서대로 적용한다. */
    private static final Pattern CERT_SEPARATOR_PATTERN = Pattern.compile("[\\s\\-_·/()\\[\\].,]");
    private static final Pattern CERT_NOISE_PATTERN =
            Pattern.compile("(필기|실기|합격|취득|자격증|예정|준비중|보유)");

    /** 표준 표기와 다른 자주 쓰이는 별칭을 표준 표기로 모은다. canonicalCert()를 거친 뒤(공백 제거·대문자) 매칭된다. */
    private static final Map<String, String> CERT_ALIASES = Map.ofEntries(
            Map.entry("SQL개발자", "SQLD"),
            Map.entry("SQL전문가", "SQLP"),
            Map.entry("데이터분석준전문가", "ADSP"),
            Map.entry("데이터분석전문가", "ADP"),
            Map.entry("AWSSOLUTIONSARCHITECTASSOCIATE", "AWSSAA"),
            Map.entry("AWS솔루션스아키텍트어소시에이트", "AWSSAA"),
            Map.entry("AWS솔루션즈아키텍트어소시에이트", "AWSSAA"),
            Map.entry("AWS솔루션스아키텍트", "AWSSAA")
    );

    /**
     * 자격증별 가중치. key는 canonicalCert()의 출력과 정확히 같은 형태여야 한다
     * (대문자, 공백·괄호 등 구분기호 없음). 운영 DB의 합격자 자격증 9종 전부를 포함한다.
     * 새 자격증을 추가할 땐 canonicalCert("원본 표기")를 직접 호출해본 결과를 key로 써야 한다.
     */
    private static final Map<String, Double> CERT_WEIGHTS = Map.ofEntries(
            Map.entry("정보처리기사", 2.0),
            Map.entry("빅데이터분석기사", 2.0),
            Map.entry("정보보안기사", 2.0),
            Map.entry("SQLP", 2.0),
            Map.entry("SQLD", 1.5),
            Map.entry("AWSSAA", 1.5),
            Map.entry("ADP", 1.5),
            Map.entry("ADSP", 1.0),
            Map.entry("리눅스마스터2급", 1.0),
            Map.entry("네트워크관리사2급", 1.0),
            Map.entry("웹디자인기능사", 0.5)
    );

    /**
     * 유저 스펙과 합격자 케이스 목록을 비교하여 총점과 상세 내역(CompareRow)을 포함한 결과를 반환한다.
     */
    public MatchScoreResult calculate(UserSpec userSpec, List<PasserData> passerList) {
        if (passerList == null || passerList.isEmpty() || userSpec == null) {
            return MatchScoreResult.builder()
                    .totalScore(0)
                    .compareRows(List.of())
                    .build();
        }

        // 1. 총점 계산 (기존 방식 유지)
        double totalScore = passerList.stream()
                .mapToDouble(passer -> calculateSingle(userSpec, passer))
                .average()
                .orElse(0.0);

        // 2. 항목별 세부 내역(CompareRow) 계산
        List<CompareRowDto> rows = calculateDetails(userSpec, passerList);

        return MatchScoreResult.builder()
                .totalScore((int) Math.round(totalScore))
                .compareRows(rows)
                .build();
    }

    private double calculateSingle(UserSpec userSpec, PasserData passer) {
        if (passer == null) return 0.0;

        double gpaScore  = scoreGpa(
                userSpec.getGpa(), userSpec.getGpaMax(),
                passer.getGpa(), passer.getGpaMax());
        double langScore = scoreLang(userSpec.getLanguageScores(), passer.getLanguageScores());
        double certScore = scoreCert(userSpec.getCertifications(), passer.getCertifications());

        return gpaScore  * WEIGHT_GPA
             + langScore * WEIGHT_LANG
             + certScore * WEIGHT_CERT;
    }

    private List<CompareRowDto> calculateDetails(UserSpec userSpec, List<PasserData> passerList) {
        // --- 1. 학점 (GPA) ---
        double userGpaNorm = normalizeGpaTo45(userSpec.getGpa(), userSpec.getGpaMax());
        double avgPasserGpaNorm = passerList.stream()
                .mapToDouble(p -> normalizeGpaTo45(p.getGpa(), p.getGpaMax()))
                .average()
                .orElse(0.0);
        
        CompareRowDto gpaRow = CompareRowDto.builder()
                .label("학점")
                .weight("40%")
                .myVal(userGpaNorm > 0 ? String.format("%.2f/4.5", userGpaNorm) : "미입력")
                .avgVal(avgPasserGpaNorm > 0 ? String.format("%.2f/4.5", avgPasserGpaNorm) : "없음")
                .myPct((int) Math.min(100, Math.round((userGpaNorm / 4.5) * 100)))
                .avgPct((int) Math.min(100, Math.round((avgPasserGpaNorm / 4.5) * 100)))
                .status(userGpaNorm >= avgPasserGpaNorm ? "충족" : "부족")
                .build();

        // --- 2. 어학 성적 (Language) ---
        double userMaxToeic = getMaxEquivalentToeic(userSpec.getLanguageScores());
        double avgPasserToeic = passerList.stream()
                .mapToDouble(p -> getMaxEquivalentToeic(p.getLanguageScores()))
                .average()
                .orElse(0.0);

        CompareRowDto langRow = CompareRowDto.builder()
                .label("어학 성적")
                .weight("33.3%")
                .myVal(userMaxToeic > 0 ? String.format("환산 %d", (int) userMaxToeic) : "없음")
                .avgVal(avgPasserToeic > 0 ? String.format("환산 %d", (int) avgPasserToeic) : "없음")
                .myPct((int) Math.min(100, Math.round((userMaxToeic / 990.0) * 100)))
                .avgPct((int) Math.min(100, Math.round((avgPasserToeic / 990.0) * 100)))
                .status(userMaxToeic >= avgPasserToeic ? "충족" : "부족")
                .build();

        // --- 3. 자격증 (Certifications) ---
        // 화면 표시는 여전히 "개수"를 쓴다 (사용자에게 익숙한 단위). 단, 충족/부족 판정과 막대 길이는
        // scoreCert와 같은 certValue(가중 합계) 기준으로 통일한다. 예전엔 이 둘이 서로 다른 공식이라
        // "비교탭엔 충족인데 종합 점수엔 0점"처럼 같은 화면 안에서 모순되는 경우가 있었다.
        int userCertCount = (userSpec.getCertifications() != null) ? (int) Arrays.stream(userSpec.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0;
        double avgPasserCertCount = passerList.stream()
                .mapToDouble(p -> (p.getCertifications() != null) ? Arrays.stream(p.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0.0)
                .average()
                .orElse(0.0);

        double userCertValue = certValue(userSpec.getCertifications());
        double avgPasserCertValue = passerList.stream()
                .mapToDouble(p -> certValue(p.getCertifications()))
                .average()
                .orElse(0.0);

        // 막대 기준: 합격자 평균 가중치의 1.5배를 100%로 둔다 (평균이 약 66% 지점에 오도록).
        // 합격자 평균이 0이면(전원 자격증 없음) 분모를 1로 고정해 0으로 나누는 것을 막는다.
        double certScale = Math.max(avgPasserCertValue * 1.5, 1.0);

        CompareRowDto certRow = CompareRowDto.builder()
                .label("자격증/수상")
                .weight("26.7%")
                .myVal(String.format("%d개", userCertCount))
                .avgVal(String.format("%.1f개", avgPasserCertCount))
                .myPct((int) Math.min(100, Math.round(userCertValue / certScale * 100)))
                .avgPct((int) Math.min(100, Math.round(avgPasserCertValue / certScale * 100)))
                .status(userCertValue >= avgPasserCertValue ? "충족" : "부족")
                .build();

        return List.of(gpaRow, langRow, certRow);
    }

    private double normalizeGpaTo45(BigDecimal gpa, BigDecimal gpaMax) {
        if (gpa == null || gpaMax == null || gpaMax.signum() <= 0) return 0.0;
        return (gpa.doubleValue() / gpaMax.doubleValue()) * 4.5;
    }

    private double getMaxEquivalentToeic(List<Map<String, Object>> langScores) {
        if (langScores == null || langScores.isEmpty()) return 0.0;
        return langScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .mapToDouble(this::convertToEquivalentToeic)
                .max()
                .orElse(0.0);
    }

    private double scoreGpa(BigDecimal userGpa, BigDecimal userGpaMax,
                            BigDecimal passerGpa, BigDecimal passerGpaMax) {
        // 합격자 쪽 학점이 없으면 비교 자체가 불가능하다. 사용자 잘못이 아니므로 감점하지 않는다
        // (합격자가 자격증을 갖고 있지 않을 때 scoreCert가 100을 주는 것과 같은 원칙).
        if (passerGpa == null || passerGpaMax == null || passerGpaMax.signum() <= 0) {
            return 100.0;
        }
        // 사용자가 입력하지 않은 항목은 0점이다.
        // 예전에는 50점을 줘서, 스펙을 하나도 넣지 않아도 종합 점수가 37점쯤 나오는 문제가 있었다.
        if (userGpa == null || userGpaMax == null || userGpaMax.signum() <= 0) {
            return 0.0;
        }

        double userVal = userGpa.doubleValue() / userGpaMax.doubleValue();
        double passerVal = passerGpa.doubleValue() / passerGpaMax.doubleValue();
        if (passerVal <= 0) return 100.0;
        if (userVal >= passerVal) return 100.0;
        return Math.max(0.0, (userVal / passerVal) * 100.0);
    }

    private double scoreLang(List<Map<String, Object>> userLangScores,
                             List<Map<String, Object>> passerLangScores) {
        // 합격자가 어학 성적을 갖고 있지 않으면 이 항목은 변별력이 없다 → 감점하지 않는다.
        double passerMaxToeic = getMaxEquivalentToeic(passerLangScores);
        if (passerMaxToeic <= 0) {
            return 100.0;
        }

        // 사용자가 입력하지 않은 항목은 0점 (예전 50점 기본값 제거).
        double userMaxToeic = getMaxEquivalentToeic(userLangScores);
        if (userMaxToeic <= 0) {
            return 0.0;
        }

        if (userMaxToeic >= passerMaxToeic) {
            return 100.0;
        }

        return (userMaxToeic / passerMaxToeic) * 100.0;
    }

    private double convertToEquivalentToeic(Map<String, Object> scoreData) {
        String type = normalize(String.valueOf(scoreData.get("type")));

        if ("TOEIC".equals(type)) {
            Double score = asDouble(scoreData.get("score"));
            return score != null ? score : 0;
        }

        if ("OPIC".equals(type)) {
            String grade = normalize(asString(scoreData.get("grade")));
            return switch (grade) {
                case "AL" -> 950.0;
                case "IH" -> 900.0;
                case "IM3" -> 800.0;
                case "IM2" -> 750.0;
                case "IM1" -> 700.0;
                case "IL" -> 600.0;
                case "NH", "NM", "NL" -> 500.0;
                default -> 0.0;
            };
        }

        if ("TOEFL".equals(type)) {
            Double score = asDouble(scoreData.get("score"));
            if (score == null) return 0.0;
            
            if (score >= 108) return 950.0 + ((score - 108) / (120 - 108) * 40.0);
            if (score >= 101) return interpolate(score, 101, 108, 900, 950);
            if (score >= 94) return interpolate(score, 94, 101, 850, 900);
            if (score >= 86) return interpolate(score, 86, 94, 800, 850);
            if (score >= 77) return interpolate(score, 77, 86, 750, 800);
            if (score >= 71) return interpolate(score, 71, 77, 700, 750);
            if (score >= 68) return interpolate(score, 68, 71, 650, 700);
            if (score >= 62) return interpolate(score, 62, 68, 600, 650);
            return (score / 62.0) * 600.0;
        }

        return 0.0;
    }

    private double interpolate(double x, double x0, double x1, double y0, double y1) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    /**
     * 합격자 자격증(passerCerts)과 사용자 자격증(userCerts)의 가중 합계를 비교한다.
     * 학점(scoreGpa)·어학(scoreLang)과 같은 "비율" 구조로 통일했다 —
     * 예전엔 자격증만 "합격자 목록에 있는 걸 몇 개 맞혔나"를 셌는데,
     * 이 방향은 사용자가 합격자보다 훨씬 많은 자격증을 갖고 있어도
     * 합격자가 안 가진 자격증이면 전혀 반영되지 않는 문제가 있었다.
     */
    private double scoreCert(String[] userCerts, String[] passerCerts) {
        double passerValue = certValue(passerCerts);
        // 합격자 쪽에 자격증이 없으면 이 항목은 변별력이 없다 → 감점하지 않는다
        // (scoreGpa·scoreLang에서 합격자 데이터가 없을 때 100점을 주는 것과 같은 원칙).
        if (passerValue <= 0) return 100.0;

        double userValue = certValue(userCerts);
        if (userValue <= 0) return 0.0;

        return Math.min(100.0, (userValue / passerValue) * 100.0);
    }

    /**
     * 자격증 문자열 배열의 가중 합계.
     * 같은 자격증을 중복 표기해도(정규화 후 같아지면) 한 번만 센다.
     *
     * ⚠️ 게이밍 방지: CERT_WEIGHTS에 없는(=기본 가중치 DEFAULT_CERT_WEIGHT를 받는) 자격증은
     * 최대 MAX_DEFAULT_WEIGHT_CERTS개까지만 인정한다. 상한이 없으면 의미 없는 문자열을
     * 여러 개 적어 넣는 것만으로 점수를 채울 수 있다(기본 가중치 0.5 × 6개 = 3.0으로
     * 합격자 평균과 같아져 100점이 되는 식).
     */
    private double certValue(String[] certs) {
        if (certs == null) return 0.0;

        List<String> canonical = Arrays.stream(certs)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        double knownSum = canonical.stream()
                .filter(CERT_WEIGHTS::containsKey)
                .mapToDouble(CERT_WEIGHTS::get)
                .sum();

        long unknownCount = canonical.stream()
                .filter(value -> !CERT_WEIGHTS.containsKey(value))
                .count();
        double unknownSum = Math.min(unknownCount, MAX_DEFAULT_WEIGHT_CERTS) * DEFAULT_CERT_WEIGHT;

        return knownSum + unknownSum;
    }

    /**
     * 자격증 표기를 정규화한다. "정보처리기사 필기" · "정보처리기사(필기)" 같은 변형을
     * "정보처리기사"로, 별칭("SQL개발자")은 CERT_ALIASES를 통해 표준 표기("SQLD")로 모은다.
     *
     * ⚠️ CERT_WEIGHTS의 key는 반드시 이 함수의 출력과 정확히 같은 형태(공백 없음, 대문자)로
     * 적어야 한다. 하나라도 어긋나면 예외 없이 조용히 DEFAULT_CERT_WEIGHT로 떨어진다 —
     * MatchScoreCalculatorTest의 "운영 DB 자격증이 기본 가중치로 떨어지지 않는지" 테스트가
     * 이 정합성을 지킨다.
     */
    private String canonicalCert(String raw) {
        if (raw == null) return "";
        String value = raw.toUpperCase(Locale.ROOT);
        value = CERT_SEPARATOR_PATTERN.matcher(value).replaceAll("");
        value = CERT_NOISE_PATTERN.matcher(value).replaceAll("");
        if (value.isBlank()) return "";
        return CERT_ALIASES.getOrDefault(value, value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
