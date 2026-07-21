package com.career.recommendation.service;

import com.career.recommendation.entity.UserSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * BE-1 담당 — 합격자 스펙과 사용자 스펙 간 유사도 점수(matchScore) 계산
 *
 * 가중치 (초기 가설 — 컴공생 기준, 사용자 피드백으로 조정 예정):
 *   학점(GPA)      : 35%
 *   어학(Language) : 25%
 *   자격증          : 20%
 *   경험(활동)      : 20%
 *
 * 반환 범위: 0 ~ 100 (정수)
 */
@Slf4j
@Component
public class MatchScoreCalculator {

    // ─── 가중치 (합산 = 1.0) ──────────────────────────────────────────────────
    private static final double WEIGHT_GPA          = 0.35;
    private static final double WEIGHT_LANGUAGE     = 0.25;
    private static final double WEIGHT_CERT         = 0.20;
    private static final double WEIGHT_EXPERIENCE   = 0.20;

    /**
     * 사용자 스펙과 합격자 스펙(PasserData 정보)을 비교해 matchScore를 계산합니다.
     *
     * @param userSpec         사용자 스펙 엔티티
     * @param passerGpa        합격자 학점 (null 허용 — 데이터 없는 경우)
     * @param passerLangScore  합격자 어학 점수 Map (예: {"type":"TOEIC","score":850})
     * @param passerCerts      합격자 자격증 목록
     * @param passerExpCount   합격자 경험 활동 수
     * @return 0~100 정수 matchScore
     */
    public int calculate(UserSpec userSpec,
                         BigDecimal passerGpa,
                         Map<String, Object> passerLangScore,
                         String[] passerCerts,
                         int passerExpCount) {

        double gpaScore      = calculateGpaScore(userSpec.getGpa(), passerGpa, userSpec.getGpaMax());
        double languageScore = calculateLanguageScore(userSpec.getLanguageScore(), passerLangScore);
        double certScore     = calculateCertScore(userSpec.getCertifications(), passerCerts);
        double expScore      = calculateExperienceScore(userSpec.getExperiences(), passerExpCount);

        double total = (gpaScore      * WEIGHT_GPA)
                     + (languageScore  * WEIGHT_LANGUAGE)
                     + (certScore      * WEIGHT_CERT)
                     + (expScore       * WEIGHT_EXPERIENCE);

        int result = (int) Math.round(total * 100);
        log.debug("matchScore 계산 — GPA:{:.1f} LANG:{:.1f} CERT:{:.1f} EXP:{:.1f} → {}",
                gpaScore, languageScore, certScore, expScore, result);
        return Math.min(100, Math.max(0, result));
    }

    /**
     * 합격자 목록이 없을 때 데이터 부족 여부를 확인합니다.
     */
    public boolean isDataSufficient(int passerCount) {
        return passerCount >= 3;   // 최소 3건 이상 있어야 비교 의미 있음
    }

    // ─── 개별 점수 계산 ───────────────────────────────────────────────────────

    /**
     * 학점 점수 (0.0 ~ 1.0)
     * 사용자 학점 / 합격자 학점 비율로 계산 (합격자보다 높으면 1.0 만점)
     */
    private double calculateGpaScore(BigDecimal userGpa, BigDecimal passerGpa, BigDecimal gpaMax) {
        if (userGpa == null || passerGpa == null || passerGpa.compareTo(BigDecimal.ZERO) == 0) {
            return 0.5;  // 데이터 없으면 중간값 처리
        }
        // 4.5 만점 기준으로 각각 정규화한 뒤 비교
        BigDecimal maxScale = gpaMax != null ? gpaMax : new BigDecimal("4.5");
        double userNorm   = userGpa.divide(maxScale, 4, RoundingMode.HALF_UP).doubleValue();
        double passerNorm = passerGpa.divide(maxScale, 4, RoundingMode.HALF_UP).doubleValue();

        if (userNorm >= passerNorm) return 1.0;
        return userNorm / passerNorm;
    }

    /**
     * 어학 점수 (0.0 ~ 1.0)
     * 동일 시험 기준으로 사용자 / 합격자 비율 계산
     * 시험 종류가 다르거나 데이터 없으면 0.5 반환
     */
    private double calculateLanguageScore(Map<String, Object> userLang, Map<String, Object> passerLang) {
        if (userLang == null || passerLang == null) return 0.5;

        String userType   = (String) userLang.get("type");
        String passerType = (String) passerLang.get("type");

        if (userType == null || !userType.equalsIgnoreCase(passerType)) {
            return 0.5;  // 시험 종류 다르면 중간값
        }

        try {
            double userScore   = toDouble(userLang.get("score"));
            double passerScore = toDouble(passerLang.get("score"));

            double maxScore = getMaxScoreForType(userType);
            if (maxScore <= 0 || passerScore <= 0) return 0.5;

            double userRatio   = userScore / maxScore;
            double passerRatio = passerScore / maxScore;
            return userRatio >= passerRatio ? 1.0 : userRatio / passerRatio;
        } catch (Exception e) {
            log.warn("어학 점수 계산 실패: {}", e.getMessage());
            return 0.5;
        }
    }

    /**
     * 자격증 점수 (0.0 ~ 1.0)
     * 합격자 자격증 중 사용자가 보유한 비율
     */
    private double calculateCertScore(String[] userCerts, String[] passerCerts) {
        if (passerCerts == null || passerCerts.length == 0) return 1.0;  // 합격자가 자격증 없으면 만점
        if (userCerts == null || userCerts.length == 0) return 0.0;

        List<String> userList = Arrays.asList(userCerts);
        long matched = Arrays.stream(passerCerts)
                .filter(cert -> userList.stream().anyMatch(u -> u.equalsIgnoreCase(cert)))
                .count();

        return (double) matched / passerCerts.length;
    }

    /**
     * 경험 점수 (0.0 ~ 1.0)
     * 사용자 경험 수 / 합격자 경험 수 (합격자보다 많으면 만점)
     */
    private double calculateExperienceScore(String[] userExperiences, int passerExpCount) {
        int userCount = userExperiences != null ? userExperiences.length : 0;
        if (passerExpCount <= 0) return 1.0;
        return userCount >= passerExpCount ? 1.0 : (double) userCount / passerExpCount;
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
    }

    /**
     * 시험 종류별 만점 기준 (확장 필요 시 추가)
     */
    private double getMaxScoreForType(String type) {
        return switch (type.toUpperCase()) {
            case "TOEIC"    -> 990.0;
            case "TOEFL"    -> 120.0;
            case "IELTS"    -> 9.0;
            case "OPIC"     -> 6.0;   // AL=6, IH=5, ...
            case "TOEIC_S"  -> 200.0;
            default          -> 0.0;   // 알 수 없는 시험 → 중간값 처리
        };
    }
}
