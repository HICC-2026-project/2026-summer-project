package com.career.recommendation.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — 스펙 원본(자격증 자유 텍스트·어학 성적 JSON)을 비교 가능한 표준 값으로
 * 정규화하는 공용 유틸.
 *
 * 프로필 집계(JobSpecProfileBuilder)와 위치 계산(SpecPositionCalculator)이 같은 정규화를
 * 쓰도록 한 곳에 모았다 — 집계 시점과 비교 시점의 정규화가 조금이라도 다르면
 * "합격자 쪽 표기와 사용자 쪽 표기가 정규화 결과만 달라서 안 맞는" 종류의 버그가 생긴다.
 *
 * (장기적으로는 저장 시점 정규화로 옮겨 이 유틸이 쓰기 경로에서만 불리게 하는 것이 목표다.
 * 그때까지는 읽기 경로에서 호출된다.)
 */
public final class SpecNormalizer {

    /**
     * 공백·구분기호·필기/실기 등 부가어를 지우기 위한 패턴. canonicalCert에서 이 순서대로 적용한다.
     * \s는 ASCII 공백만 매칭해 전각공백(U+3000 — 노션·한글 문서·일부 모바일 키보드에서 흔히 섞임)을
     * 놓치므로 명시적으로 추가한다.
     */
    private static final Pattern CERT_SEPARATOR_PATTERN = Pattern.compile("[\\s\\u3000\\-_·/()\\[\\].,]");
    private static final Pattern CERT_NOISE_PATTERN =
            Pattern.compile("(필기|실기|합격|취득|자격증|예정|준비중|보유)");

    /**
     * 표준 표기와 다른 자주 쓰이는 별칭·구 명칭을 표준 표기로 모은다.
     * canonicalCert()의 공백 제거·대문자화를 거친 뒤 매칭된다.
     */
    private static final Map<String, String> CERT_ALIASES = Map.ofEntries(
            Map.entry("SQL개발자", "SQLD"),
            Map.entry("SQL전문가", "SQLP"),
            Map.entry("데이터분석준전문가", "ADSP"),
            Map.entry("데이터분석전문가", "ADP"),
            Map.entry("AWSSOLUTIONSARCHITECTASSOCIATE", "AWSSAA"),
            Map.entry("AWS솔루션스아키텍트어소시에이트", "AWSSAA"),
            Map.entry("AWS솔루션즈아키텍트어소시에이트", "AWSSAA"),
            Map.entry("AWS솔루션스아키텍트", "AWSSAA"),
            // 2026년 국가기술자격 종목 개편 반영 (Q-net 확인) — 구 명칭도 표준 표기로 모은다.
            Map.entry("정보처리기능사", "프로그래밍기능사"),
            Map.entry("전자계산기조직응용기사", "컴퓨터시스템기사"),
            Map.entry("전자계산기기사", "컴퓨터시스템기사")
    );

    private SpecNormalizer() {}

    /**
     * 자격증 표기를 정규화한다. "정보처리기사 필기" · "정보처리기사(필기)" 같은 변형을
     * "정보처리기사"로, 별칭("SQL개발자")은 CERT_ALIASES를 통해 표준 표기("SQLD")로 모은다.
     * 정규화 결과가 비면(부가어만 입력) 빈 문자열을 반환한다.
     */
    public static String canonicalCert(String raw) {
        if (raw == null) return "";
        String value = raw.toUpperCase(Locale.ROOT);
        value = CERT_SEPARATOR_PATTERN.matcher(value).replaceAll("");
        value = CERT_NOISE_PATTERN.matcher(value).replaceAll("");
        if (value.isBlank()) return "";
        return CERT_ALIASES.getOrDefault(value, value);
    }

    /** 정규화 후 중복 제거된 자격증 표기 집합. 입력 순서를 유지하고 빈 값은 버린다. */
    public static Set<String> canonicalCerts(String[] certs) {
        if (certs == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String raw : certs) {
            String canonical = canonicalCert(raw);
            if (!canonical.isBlank()) {
                result.add(canonical);
            }
        }
        return result;
    }

    /**
     * 어학 성적 목록에서 토익 환산 최고점을 반환한다. 성적이 없거나 환산 불가하면 0
     * (0은 "어학 미보유"를 뜻한다 — 학점과 달리 어학은 실제 0이 존재하지 않는 값이라
     * 결측 플래그를 따로 두지 않는다).
     */
    public static double maxEquivalentToeic(List<Map<String, Object>> langScores) {
        if (langScores == null || langScores.isEmpty()) return 0.0;
        return langScores.stream()
                .filter(score -> score != null && score.get("type") != null)
                .mapToDouble(SpecNormalizer::equivalentToeic)
                .max()
                .orElse(0.0);
    }

    private static double equivalentToeic(Map<String, Object> scoreData) {
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
                // "IM"은 세부등급(IM1~3) 미기재 표기 — 검증(@Pattern)은 통과하는데 환산표에
                // 없으면 조용히 0점(어학 미보유 취급)이 된다. 중간치인 IM2와 같게 본다.
                case "IM2", "IM" -> 750.0;
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

    private static double interpolate(double x, double x0, double x1, double y0, double y1) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
