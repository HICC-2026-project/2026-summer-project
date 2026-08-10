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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
     * 점수 산출 공식의 버전. 공식이 바뀌면 반드시 올린다.
     * RecommendationService가 캐시된 응답의 scoreFormulaVersion과 비교해,
     * 옛 버전으로 계산된 캐시는 legacy로 판정하여 다음 요청에서 한 번 강제로 재계산한다.
     * (버전을 안 올리면 배포 후에도 유저마다 옛 공식/새 공식 점수가 뒤섞여 보인다.)
     *
     * 1: 자격증 완전 일치 매칭 (배포 초기 — 자유 텍스트와 궁합이 나빠 대부분 0점)
     * 2: 자격증 이름 정규화 + 가중치 합산 매칭, 미등록 자격증은 기본 가중치로 일부 인정(상한 있음)
     * 3: "인식된 자격증만 점수가 된다"로 재설계. 미등록 자격증의 기본 가중치·상한을 없애고,
     *    대신 인식 범위를 3단으로 넓혔다(아래 CERT_WEIGHTS·NATIONAL_TECH_CERTIFICATIONS·
     *    합격자 DB 실관측 자격증). v2는 "정크 자격증 여러 개가 미입력보다 유리해지는" 문제가
     *    구조적으로 남아 있었다(기본 가중치 > 0인 한 상한으로 크기만 줄일 뿐 방향은 못 바꿈).
     */
    public static final int CURRENT_SCORE_FORMULA_VERSION = 3;

    /**
     * 표(CERT_WEIGHTS)에 없어도 NATIONAL_TECH_CERTIFICATIONS나 합격자 DB에서 실제로 관측된
     * 자격증에 부여하는 가중치. "존재가 검증된" 자격증에만 적용되므로 CERT_WEIGHTS처럼
     * 자격증별로 차등을 두지 않고 중간값 하나로 통일한다.
     */
    private static final double RECOGNIZED_DEFAULT_WEIGHT = 1.0;

    /**
     * 공백·구분기호·필기/실기 등 부가어를 지우기 위한 패턴. canonicalCert에서 이 순서대로 적용한다.
     * \s는 ASCII 공백만 매칭해 전각공백(U+3000 — 노션·한글 문서·일부 모바일 키보드에서 흔히 섞임)을
     * 놓친다. 놓치면 "SQLD　필기"가 "SQLD　"로 남아 CERT_WEIGHTS의 "SQLD"와 안 맞고
     * 예외 없이 조용히 미인식 처리되므로 명시적으로 추가한다.
     */
    private static final Pattern CERT_SEPARATOR_PATTERN = Pattern.compile("[\\s\\u3000\\-_·/()\\[\\].,]");
    private static final Pattern CERT_NOISE_PATTERN =
            Pattern.compile("(필기|실기|합격|취득|자격증|예정|준비중|보유)");

    /**
     * 표준 표기와 다른 자주 쓰이는 별칭·구 명칭을 표준 표기로 모은다.
     * canonicalCert()를 거친 뒤(공백 제거·대문자) 매칭된다.
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
            Map.entry("정보처리기능사", "프로그래밍기능사"),   // 2026.1.1 명칭 변경
            Map.entry("전자계산기조직응용기사", "컴퓨터시스템기사"), // 2026년 전자계산기기사와 통합
            Map.entry("전자계산기기사", "컴퓨터시스템기사")
    );

    /**
     * 자격증별 가중치(1층 — 큐레이션 표). key는 canonicalCert()의 출력과 정확히 같은 형태여야 한다
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
     * 3층 — 국가기술자격 종목명(정보기술 분야). Q-net(q-net.or.kr)·cq.or.kr 검색으로 확인한
     * 2026년 8월 기준 실재 종목만 넣었다(기억으로 채우지 않음 — 틀린 종목명을 넣으면 애초에
     * 고치려던 "조용히 미인식되는" 문제를 그대로 재생산한다).
     *
     * CERT_WEIGHTS와 달리 종목별 가중치를 차등하지 않고 RECOGNIZED_DEFAULT_WEIGHT로 통일한다 —
     * 이 표의 목적은 "존재를 검증하는 것"이지 난이도를 매기는 것이 아니다.
     *
     * ⚠️ 이 목록은 완전하지 않을 수 있다. 데모 전, 실제 사용자가 자주 입력할 만한 자격증이
     * 빠져 있지 않은지 Q-net 공식 목록과 대조해 팀에서 한 번 더 검증해야 한다.
     */
    private static final Set<String> NATIONAL_TECH_CERTIFICATIONS = Set.of(
            // 기술사
            "정보관리기술사", "컴퓨터시스템응용기술사", "정보통신기술사",
            // 기사
            "정보처리기사", "정보보안기사", "빅데이터분석기사",
            "정보통신기사", "컴퓨터시스템기사", "무선설비기사", "방송통신기사", "전파전자통신기사",
            // 산업기사
            "정보처리산업기사", "정보보안산업기사", "사무자동화산업기사",
            "정보통신산업기사", "무선설비산업기사", "방송통신산업기사", "전파전자통신산업기사",
            // 기능장 · 기능사
            "통신설비기능장", "프로그래밍기능사", "정보기기운용기능사",
            "정보통신기능사", "무선설비기능사", "방송통신기능사", "전파전자통신기능사"
    );

    /**
     * 유저 스펙과 합격자 케이스 목록을 비교하여 총점과 상세 내역(CompareRow)을 포함한 결과를 반환한다.
     */
    public MatchScoreResult calculate(UserSpec userSpec, List<PasserData> passerList) {
        if (passerList == null || passerList.isEmpty() || userSpec == null) {
            return MatchScoreResult.builder()
                    .totalScore(0)
                    .compareRows(List.of())
                    .unrecognizedCertifications(List.of())
                    .build();
        }

        // 2층 — 합격자 DB에 실제로 등장한 자격증은 그 자체로 실재가 검증된다.
        // CERT_WEIGHTS·NATIONAL_TECH_CERTIFICATIONS에 없어도 이 집합에 있으면 인정한다.
        // 비교 대상 합격자 개인이 아니라 전체 후보 풀(passerList) 기준으로 모아,
        // "이 직무군 어딘가에서 실제로 관측된 적 있는 자격증"을 폭넓게 인정한다.
        Set<String> observedCerts = collectObservedCerts(passerList);

        // 1. 총점 계산 (기존 방식 유지)
        double totalScore = passerList.stream()
                .mapToDouble(passer -> calculateSingle(userSpec, passer, observedCerts))
                .average()
                .orElse(0.0);

        // 2. 항목별 세부 내역(CompareRow) 계산
        List<CompareRowDto> rows = calculateDetails(userSpec, passerList, observedCerts);

        // 3. 사용자가 입력했지만 어느 층에서도 인식하지 못한 자격증 — 화면에 고지해
        //    "왜 이 자격증은 반영 안 됐지?"를 사용자가 스스로 확인·수정할 수 있게 한다.
        List<String> unrecognized = unrecognizedCertifications(userSpec.getCertifications(), observedCerts);

        return MatchScoreResult.builder()
                .totalScore((int) Math.round(totalScore))
                .compareRows(rows)
                .unrecognizedCertifications(unrecognized)
                .build();
    }

    private Set<String> collectObservedCerts(List<PasserData> passerList) {
        return passerList.stream()
                .filter(Objects::nonNull)
                .map(PasserData::getCertifications)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private double calculateSingle(UserSpec userSpec, PasserData passer, Set<String> observedCerts) {
        if (passer == null) return 0.0;

        double gpaScore  = scoreGpa(
                userSpec.getGpa(), userSpec.getGpaMax(),
                passer.getGpa(), passer.getGpaMax());
        double langScore = scoreLang(userSpec.getLanguageScores(), passer.getLanguageScores());
        double certScore = scoreCert(userSpec.getCertifications(), passer.getCertifications(), observedCerts);

        return gpaScore  * WEIGHT_GPA
             + langScore * WEIGHT_LANG
             + certScore * WEIGHT_CERT;
    }

    private List<CompareRowDto> calculateDetails(UserSpec userSpec, List<PasserData> passerList, Set<String> observedCerts) {
        // --- 1. 학점 (GPA) ---
        // 학점 데이터가 없는 합격자는 평균 계산에서 제외한다 — 포함시키면 0으로 잡혀 평균이
        // 실제보다 크게 낮아진다. scoreGpa()가 합격자 학점 결측을 "비교 불가(감점 없음)"로
        // 처리하는 것과 같은 원칙이다. 정상 학점이 0점일 수는 없으므로 >0 필터로 결측을 걸러낸다.
        double userGpaNorm = normalizeGpaTo45(userSpec.getGpa(), userSpec.getGpaMax());
        double avgPasserGpaNorm = passerList.stream()
                .mapToDouble(p -> normalizeGpaTo45(p.getGpa(), p.getGpaMax()))
                .filter(value -> value > 0)
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
        // GPA와 같은 이유로, 어학 성적이 없는(또는 환산 불가한) 합격자는 평균에서 제외한다.
        double userMaxToeic = getMaxEquivalentToeic(userSpec.getLanguageScores());
        double avgPasserToeic = passerList.stream()
                .mapToDouble(p -> getMaxEquivalentToeic(p.getLanguageScores()))
                .filter(value -> value > 0)
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
        //
        // GPA·어학과 달리 여기엔 ">0 필터"를 일부러 안 넣는다 — 자격증 0개는 "데이터 결측"이 아니라
        // "실제로 안 가짐"이라는 유효한 값이라, 평균에서 빼면 오히려 왜곡된다.
        int userCertCount = (userSpec.getCertifications() != null) ? (int) Arrays.stream(userSpec.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0;
        double avgPasserCertCount = passerList.stream()
                .mapToDouble(p -> (p.getCertifications() != null) ? Arrays.stream(p.getCertifications()).filter(s -> s != null && !s.isBlank()).count() : 0.0)
                .average()
                .orElse(0.0);

        double userCertValue = certValue(userSpec.getCertifications(), observedCerts);
        double avgPasserCertValue = passerList.stream()
                .mapToDouble(p -> certValue(p.getCertifications(), observedCerts))
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
    private double scoreCert(String[] userCerts, String[] passerCerts, Set<String> observedCerts) {
        double passerValue = certValue(passerCerts, observedCerts);
        // 합격자 쪽에 자격증이 없으면 이 항목은 변별력이 없다 → 감점하지 않는다
        // (scoreGpa·scoreLang에서 합격자 데이터가 없을 때 100점을 주는 것과 같은 원칙).
        if (passerValue <= 0) return 100.0;

        double userValue = certValue(userCerts, observedCerts);
        if (userValue <= 0) return 0.0;

        return Math.min(100.0, (userValue / passerValue) * 100.0);
    }

    /**
     * 자격증 문자열 배열의 가중 합계. 같은 자격증을 중복 표기해도(정규화 후 같아지면) 한 번만 센다.
     *
     * v2까지는 "인식 못 한 자격증도 기본 가중치로 일부 인정"했는데, 이 기본 가중치가 0보다 큰 한
     * 정크 문자열을 적는 게 아예 안 적는 것보다 항상 유리해지는 문제가 수학적으로 있었다
     * (상한을 둬도 크기만 줄 뿐 방향은 못 바꾼다). v3는 원칙을 바꿨다 — 인식되지 않은 입력은
     * 기여가 정확히 0이다. weightOf()가 그 판정을 담당한다.
     */
    private double certValue(String[] certs, Set<String> observedCerts) {
        if (certs == null) return 0.0;

        return Arrays.stream(certs)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .distinct()
                .mapToDouble(value -> weightOf(value, observedCerts))
                .sum();
    }

    /**
     * 정규화된 자격증 표기 하나의 가중치를 3층으로 판정한다.
     *   1층 CERT_WEIGHTS   — 큐레이션 표, 자격증별 차등 가중치
     *   2층 observedCerts  — 합격자 DB에 실제로 등장(런타임 관측), RECOGNIZED_DEFAULT_WEIGHT
     *   3층 NATIONAL_TECH_CERTIFICATIONS — 국가기술자격 공식 종목명, RECOGNIZED_DEFAULT_WEIGHT
     * 세 층 어디에도 없으면 0 — 미인식 입력은 점수에 전혀 기여하지 않는다.
     */
    private double weightOf(String canonicalCert, Set<String> observedCerts) {
        if (CERT_WEIGHTS.containsKey(canonicalCert)) {
            return CERT_WEIGHTS.get(canonicalCert);
        }
        if (NATIONAL_TECH_CERTIFICATIONS.contains(canonicalCert) || observedCerts.contains(canonicalCert)) {
            return RECOGNIZED_DEFAULT_WEIGHT;
        }
        return 0.0;
    }

    /**
     * 사용자가 입력한 자격증 중 세 층 어디에서도 인식하지 못한 항목을 원본 표기 그대로 반환한다.
     * 화면에 고지해 사용자가 오타·특이 표기를 스스로 고칠 수 있게 하기 위함이다 — 조용히
     * 버리기만 하면 "왜 이건 반영 안 됐지?"에 코드를 보지 않고는 답할 수 없다.
     */
    private List<String> unrecognizedCertifications(String[] userCerts, Set<String> observedCerts) {
        if (userCerts == null) return List.of();
        return Arrays.stream(userCerts)
                .filter(raw -> raw != null && !raw.isBlank())
                .filter(raw -> weightOf(canonicalCert(raw), observedCerts) <= 0)
                .distinct()
                .toList();
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
