package com.career.recommendation.util;

import com.career.recommendation.dto.recommendation.CompareRowDto;
import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        int userCertCount = (userSpec.getCertifications() != null) ? (int) Arrays.stream(userSpec.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0;
        double avgPasserCertCount = passerList.stream()
                .mapToDouble(p -> (p.getCertifications() != null) ? Arrays.stream(p.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0.0)
                .average()
                .orElse(0.0);

        // 자격증은 최대 5개를 100% 기준으로 표시 (1개당 20%)
        CompareRowDto certRow = CompareRowDto.builder()
                .label("자격증/수상")
                .weight("26.7%")
                .myVal(String.format("%d개", userCertCount))
                .avgVal(String.format("%.1f개", avgPasserCertCount))
                .myPct((int) Math.min(100, Math.round(userCertCount * 20.0)))
                .avgPct((int) Math.min(100, Math.round(avgPasserCertCount * 20.0)))
                .status(userCertCount >= avgPasserCertCount ? "충족" : "부족")
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
        if (userGpa == null || userGpaMax == null
                || passerGpa == null || passerGpaMax == null
                || userGpaMax.signum() <= 0 || passerGpaMax.signum() <= 0) {
            return 50.0;
        }

        double userVal = userGpa.doubleValue() / userGpaMax.doubleValue();
        double passerVal = passerGpa.doubleValue() / passerGpaMax.doubleValue();
        if (passerVal <= 0) return 50.0;
        if (userVal >= passerVal) return 100.0;
        return Math.max(0.0, (userVal / passerVal) * 100.0);
    }

    private double scoreLang(List<Map<String, Object>> userLangScores,
                             List<Map<String, Object>> passerLangScores) {
        if (userLangScores == null || userLangScores.isEmpty()
                || passerLangScores == null || passerLangScores.isEmpty()) {
            return 50.0;
        }

        double userMaxToeic = getMaxEquivalentToeic(userLangScores);
        double passerMaxToeic = getMaxEquivalentToeic(passerLangScores);

        if (userMaxToeic <= 0 || passerMaxToeic <= 0) {
            return 50.0;
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

    private double scoreCert(String[] userCerts, String[] passerCerts) {
        if (passerCerts == null || passerCerts.length == 0) return 100.0;
        if (userCerts == null || userCerts.length == 0)    return 0.0;

        Set<String> userSet = Arrays.stream(userCerts)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        long matched = Arrays.stream(passerCerts)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .filter(userSet::contains)
                .count();
        return ((double) matched / passerCerts.length) * 100.0;
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
