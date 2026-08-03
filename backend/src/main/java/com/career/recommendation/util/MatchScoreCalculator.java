package com.career.recommendation.util;

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
 * BE-1 담당 — 유저 스펙과 합격자 데이터를 비교하여 matchScore(0~100)를 계산한다.
 *
 * UserSpec에서 경험 필드가 제거되어 비교 가능한 세 항목의 기존 비중(30:25:20)을
 * 합계 100%로 재조정한다:
 *   - 학점        40%
 *   - 어학 점수    33.33%
 *   - 자격증       26.67%
 */
@Component
public class MatchScoreCalculator {

    private static final double WEIGHT_GPA  = 0.40;
    private static final double WEIGHT_LANG = 1.0 / 3.0;
    private static final double WEIGHT_CERT = 4.0 / 15.0;

    private static final Map<String, Integer> OPIC_RANKS = Map.ofEntries(
            Map.entry("NL", 0),
            Map.entry("NM", 1),
            Map.entry("NH", 2),
            Map.entry("IL", 3),
            Map.entry("IM", 4),
            Map.entry("IM1", 4),
            Map.entry("IM2", 5),
            Map.entry("IM3", 6),
            Map.entry("IH", 7),
            Map.entry("AL", 8)
    );

    /**
     * 유저 스펙과 합격자 케이스 목록을 비교하여 평균 matchScore를 반환한다.
     * 합격자가 없으면 0을 반환한다.
     */
    public int calculate(UserSpec userSpec, List<PasserData> passerList) {
        if (passerList == null || passerList.isEmpty()) {
            return 0;
        }
        // userSpec이 null이면 스펙 비교 불가 → 0 반환
        if (userSpec == null) {
            return 0;
        }

        double totalScore = passerList.stream()
                .mapToDouble(passer -> calculateSingle(userSpec, passer))
                .average()
                .orElse(0.0);

        return (int) Math.round(totalScore);
    }

    private double calculateSingle(UserSpec userSpec, PasserData passer) {
        // userSpec은 이미 calculate()에서 null 체크되었으나, passer null 방어
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

    /**
     * 학점 점수: 합격자 학점 대비 유저 학점의 상대적 위치를 0~100으로 환산.
     * 유저 학점이 합격자 학점 이상이면 100점.
     * gpaMax 기준으로 정규화하여 비율을 계산한다.
     */
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

    /**
     * 사용자와 합격자가 보유한 모든 어학 성적을 환산 토익 점수(Equivalent TOEIC Score)로 변환한 뒤,
     * 각각의 최고 점수를 비교하여 비율을 계산한다.
     */
    private double scoreLang(List<Map<String, Object>> userLangScores,
                             List<Map<String, Object>> passerLangScores) {
        if (userLangScores == null || userLangScores.isEmpty()
                || passerLangScores == null || passerLangScores.isEmpty()) {
            return 50.0;
        }

        double userMaxToeic = userLangScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .mapToDouble(this::convertToEquivalentToeic)
                .max()
                .orElse(-1);

        double passerMaxToeic = passerLangScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .mapToDouble(this::convertToEquivalentToeic)
                .max()
                .orElse(-1);

        if (userMaxToeic < 0 || passerMaxToeic <= 0) {
            return 50.0;
        }

        if (userMaxToeic >= passerMaxToeic) {
            return 100.0;
        }

        return (userMaxToeic / passerMaxToeic) * 100.0;
    }

    /**
     * 각 어학 시험 성적을 범용 토익 점수로 환산한다.
     * 지원 시험: TOEIC, TOEFL (iBT), OPIC
     */
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
            
            // TOEFL iBT to TOEIC mapping table points
            // (TOEFL, TOEIC): (62, 600), (68, 650), (71, 700), (77, 750), 
            // (86, 800), (94, 850), (101, 900), (108, 950)
            if (score >= 108) return 950.0 + ((score - 108) / (120 - 108) * 40.0); // max 990
            if (score >= 101) return interpolate(score, 101, 108, 900, 950);
            if (score >= 94) return interpolate(score, 94, 101, 850, 900);
            if (score >= 86) return interpolate(score, 86, 94, 800, 850);
            if (score >= 77) return interpolate(score, 77, 86, 750, 800);
            if (score >= 71) return interpolate(score, 71, 77, 700, 750);
            if (score >= 68) return interpolate(score, 68, 71, 650, 700);
            if (score >= 62) return interpolate(score, 62, 68, 600, 650);
            return (score / 62.0) * 600.0; // below 62
        }

        return 0.0;
    }

    private double interpolate(double x, double x0, double x1, double y0, double y1) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    /**
     * 자격증 점수: 유저가 합격자의 자격증 목록을 몇 개 보유했는지 비율로 계산.
     */
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
