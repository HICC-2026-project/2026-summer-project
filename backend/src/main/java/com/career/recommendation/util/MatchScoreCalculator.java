package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * BE-1 담당 — 유저 스펙과 합격자 데이터를 비교하여 matchScore(0~100)를 계산한다.
 *
 * 가중치 (회의록 결정):
 *   - 학점        30%
 *   - 어학 점수    25%
 *   - 자격증       20%
 *   - 경험 수      25%
 */
@Component
public class MatchScoreCalculator {

    private static final double WEIGHT_GPA   = 0.30;
    private static final double WEIGHT_LANG  = 0.25;
    private static final double WEIGHT_CERT  = 0.20;
    private static final double WEIGHT_EXP   = 0.25;

    /** 경험 수 최대 기준 (이 이상이면 만점) */
    private static final int MAX_EXP_COUNT = 5;

    /**
     * 유저 스펙과 합격자 케이스 목록을 비교하여 평균 matchScore를 반환한다.
     * 합격자가 없으면 0을 반환한다.
     */
    public int calculate(UserSpec userSpec, List<PasserData> passerList) {
        if (passerList == null || passerList.isEmpty()) {
            return 0;
        }

        double totalScore = passerList.stream()
                .mapToDouble(passer -> calculateSingle(userSpec, passer))
                .average()
                .orElse(0.0);

        return (int) Math.round(totalScore);
    }

    private double calculateSingle(UserSpec userSpec, PasserData passer) {
        double gpaScore  = scoreGpa(userSpec.getGpa(), passer.getGpa(), userSpec.getGpaMax());
        double langScore = scoreLang(userSpec.getLanguageScores(), passer.getLanguageScore());
        double certScore = scoreCert(userSpec.getCertifications(), passer.getCertifications());
        double expScore  = scoreExp(passer.getExperienceCount());

        return gpaScore  * WEIGHT_GPA
             + langScore * WEIGHT_LANG
             + certScore * WEIGHT_CERT
             + expScore  * WEIGHT_EXP;
    }

    /**
     * 학점 점수: 합격자 학점 대비 유저 학점의 상대적 위치를 0~100으로 환산.
     * 유저 학점이 합격자 학점 이상이면 100점.
     * gpaMax 기준으로 정규화하여 비율을 계산한다.
     */
    private double scoreGpa(BigDecimal userGpa, BigDecimal passerGpa, BigDecimal gpaMax) {
        if (userGpa == null || passerGpa == null) return 50.0;
        double scale = (gpaMax != null) ? gpaMax.doubleValue() : 4.5;
        double userVal   = userGpa.doubleValue() / scale;
        double passerVal = passerGpa.doubleValue() / scale;
        if (userVal >= passerVal) return 100.0;
        // 부족한 비율에 따라 감점 (최소 0점)
        double ratio = userVal / passerVal;
        return Math.max(0.0, ratio * 100.0);
    }

    /**
     * 어학 점수: 유저의 TOEIC 점수가 있으면 합격자와 비교.
     * 점수가 없거나 비교 불가능한 경우 중립 50점 처리.
     */
    private double scoreLang(List<Map<String, Object>> userLangScores, Map<String, Object> passerLangScore) {
        if (userLangScores == null || userLangScores.isEmpty() || passerLangScore == null) return 50.0;

        // TOEIC 기준 비교 (존재할 경우)
        Integer userToeic   = extractToeic(userLangScores);
        Object  passerScore = passerLangScore.get("score");

        if (userToeic == null || passerScore == null) return 50.0;

        try {
            int passerToeic = Integer.parseInt(passerScore.toString());
            if (userToeic >= passerToeic) return 100.0;
            return Math.max(0.0, ((double) userToeic / passerToeic) * 100.0);
        } catch (NumberFormatException e) {
            return 50.0;
        }
    }

    private Integer extractToeic(List<Map<String, Object>> langScores) {
        return langScores.stream()
                .filter(m -> "TOEIC".equalsIgnoreCase(String.valueOf(m.get("type"))))
                .map(m -> {
                    try { return Integer.parseInt(String.valueOf(m.get("score"))); }
                    catch (Exception e) { return null; }
                })
                .filter(s -> s != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 자격증 점수: 유저가 합격자의 자격증 목록을 몇 개 보유했는지 비율로 계산.
     */
    private double scoreCert(String[] userCerts, String[] passerCerts) {
        if (passerCerts == null || passerCerts.length == 0) return 100.0;
        if (userCerts == null || userCerts.length == 0)    return 0.0;

        List<String> userList   = Arrays.asList(userCerts);
        long matched = Arrays.stream(passerCerts)
                .filter(userList::contains)
                .count();
        return ((double) matched / passerCerts.length) * 100.0;
    }

    /**
     * 경험 수 점수: 합격자 경험 건수 기준으로 유저 경험(추후 연동)을 비교.
     * 현재는 합격자 경험 수를 MAX_EXP_COUNT 기준으로 점수화한다.
     */
    private double scoreExp(Integer passerExpCount) {
        if (passerExpCount == null || passerExpCount == 0) return 50.0;
        // 합격자 경험이 MAX 이상이면 만점이 어려운 상황을 역으로 점수화
        // (경험 수가 많을수록 합격 가능성이 높았다는 의미 → 유저에게 도전 지표로 활용)
        return Math.min(100.0, ((double) passerExpCount / MAX_EXP_COUNT) * 100.0);
    }
}
