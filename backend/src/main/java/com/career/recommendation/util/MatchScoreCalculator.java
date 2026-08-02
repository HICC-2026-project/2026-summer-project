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
     * 사용자와 합격자가 공통으로 보유한 시험끼리 비교한다.
     * 공통 시험이 여러 개면 평균을 사용하고, 비교 가능한 시험이 없으면 중립 50점 처리한다.
     */
    private double scoreLang(List<Map<String, Object>> userLangScores,
                             List<Map<String, Object>> passerLangScores) {
        if (userLangScores == null || userLangScores.isEmpty()
                || passerLangScores == null || passerLangScores.isEmpty()) {
            return 50.0;
        }

        Map<String, Map<String, Object>> passerByType = new LinkedHashMap<>();
        passerLangScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .forEach(score -> passerByType.putIfAbsent(
                        normalize(String.valueOf(score.get("type"))), score));

        return userLangScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .mapToDouble(userScore -> {
                    String type = normalize(String.valueOf(userScore.get("type")));
                    Map<String, Object> passerScore = passerByType.get(type);
                    return passerScore == null
                            ? Double.NaN
                            : scoreSameLanguageTest(type, userScore, passerScore);
                })
                .filter(score -> !Double.isNaN(score))
                .average()
                .orElse(50.0);
    }

    private double scoreSameLanguageTest(String type,
                                         Map<String, Object> userScore,
                                         Map<String, Object> passerScore) {
        if ("OPIC".equals(type)) {
            Integer userRank = OPIC_RANKS.get(normalize(asString(userScore.get("grade"))));
            Integer passerRank = OPIC_RANKS.get(normalize(asString(passerScore.get("grade"))));
            if (userRank == null || passerRank == null) return Double.NaN;
            if (userRank >= passerRank) return 100.0;
            return ((double) (userRank + 1) / (passerRank + 1)) * 100.0;
        }

        Double userValue = asDouble(userScore.get("score"));
        Double passerValue = asDouble(passerScore.get("score"));
        if (userValue == null || passerValue == null || passerValue <= 0) {
            return Double.NaN;
        }

        Double userMax = asDouble(userScore.get("maxScore"));
        Double passerMax = asDouble(passerScore.get("maxScore"));
        double normalizedUser = userMax != null && userMax > 0
                ? userValue / userMax
                : userValue;
        double normalizedPasser = passerMax != null && passerMax > 0
                ? passerValue / passerMax
                : passerValue;

        if (normalizedPasser <= 0) return Double.NaN;
        if (normalizedUser >= normalizedPasser) return 100.0;
        return Math.max(0.0, (normalizedUser / normalizedPasser) * 100.0);
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
