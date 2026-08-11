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
     * 4: 자격증 막대(myPct·avgPct)의 기준을 상대값에서 고정값(CERT_SCALE_MAX)으로 교체.
     *    총점 계산식 자체는 3과 동일하지만, compareRows가 캐시 JSON에 함께 저장되므로
     *    버전을 올리지 않으면 기존 유저는 계속 옛 막대(합격자 항상 67%)를 보게 된다.
     * 5: 학점 점수에 바닥값(GPA_FLOOR_RATIO)을 도입. 학점 축만 하위 구간을 쓰지 않아
     *    명목 가중치(40%)보다 실제 영향력이 작았던 문제를 바로잡는다.
     * 6: 자격증 가중치를 목표 직무 합격자의 보유율에서 유도(JOB_WEIGHT_MIN~MAX 보간).
     *    난이도에 대한 임의 판단 대신 "그 직무 합격자가 실제로 갖고 있는 비율"을 쓴다.
     *    직무 데이터가 없으면 기존 큐레이션 표(CERT_WEIGHTS)로 폴백한다.
     * 7: 총점 구조를 "합격자별 가중합의 평균"에서 "축별(데이터 보유 합격자만) 평균의
     *    가중합 + 결측 축 제외·가중치 재정규화"로 재구성. 합격자 결측 필드가 자동 100점으로
     *    잡혀 총점을 부풀리던 문제(비교 탭 막대는 결측을 "제외"로 처리해 화면 안에서 총점과
     *    막대가 모순돼 보이던 문제)를 바로잡는다 — calculateTotalScore 주석 참고.
     * 8: (a) 비교 행 표시 규칙을 총점과 통일 — 합격자 데이터가 없어 총점에서 제외된 축은
     *    행도 표시하지 않는다(예전엔 사용자만 값이 있으면 "충족"+명목 가중치로 그려져
     *    점수-막대 모순이 남았다). (b) 직무 유도 자격증 가중치에 최소 표본
     *    (MIN_JOB_WEIGHT_SAMPLE) 도입 — 합격자 1~2명짜리 직무에서 보유율 100%가 나와
     *    큐레이션 가중치를 뒤집던 문제. (c) OPIc "IM"(세부등급 미기재) 환산 추가 —
     *    검증은 통과하는데 환산표에 없어 조용히 0점 처리되던 값.
     */
    public static final int CURRENT_SCORE_FORMULA_VERSION = 8;

    /**
     * 직무별 자격증 가중치의 하한·상한. 해당 직무 합격자의 보유율 0~100%를 이 구간으로 편다.
     *   가중치 = JOB_WEIGHT_MIN + (JOB_WEIGHT_MAX - JOB_WEIGHT_MIN) × 보유율
     *
     * 하한을 0이 아니라 0.5로 두는 이유: 보유율 0%라도 "인식된 자격증"이면 0점이 되어선 안 된다.
     * 0점은 v3부터 "우리가 알아보지 못한 입력"에만 주는 값이고, 그 구분이 무너지면
     * 정크 입력과 실재하는 자격증이 같은 취급을 받는다.
     */
    private static final double JOB_WEIGHT_MIN = 0.5;
    private static final double JOB_WEIGHT_MAX = 2.5;

    /**
     * 직무 유도 가중치를 신뢰하기 위한 최소 합격자 수. 이 값 미만이면 보유율이 개인의
     * 우연(1명이 갖고 있으면 100%)에 좌우되므로 큐레이션 표(CERT_WEIGHTS)로 폴백한다 —
     * SimilarSpecFinder.MIN_SAMPLE(비교 표본 3명)과 같은 원칙(calculate() 주석 참고).
     */
    private static final int MIN_JOB_WEIGHT_SAMPLE = 3;

    /**
     * 학점 점수의 바닥값(정규화 학점 비율). 이 값 이하는 학점 항목에서 0점으로 본다.
     *
     * 도입 이유: 점수를 단순 비율(내 비율 / 합격자 비율)로 내면 학점 축만 하위 구간을
     * 쓰지 않는다. 현실적으로 학점 비율이 0에 가까운 사람은 없기 때문이다(운영 합격자
     * 60명의 실측 범위는 0.689~0.938). 그 결과 학점 2.5/4.5(=0.556)인 사람도 68점을 받아,
     * 어학·자격증이 0~100 전 구간을 쓰는 것과 영향력이 맞지 않았다. 실제로 이 때문에
     * "학점 3.7·어학 미입력(40점)"이 "학점 2.5·토익 500(47점)"보다 낮게 나오는
     * 역전이 발생했다.
     *
     * 값 근거: 만점의 50%(4.5 기준 2.25). 관측된 합격자 최저치(0.689)보다 충분히 낮게 잡아
     * 합격자 구간 전체가 0~100 안에 들어오도록 했다. 어학·자격증에는 같은 처리를 하지
     * 않는다 — 그 두 축은 "시험을 안 봤다/자격증이 없다"로 실제 0이 가능해서 이미
     * 전 구간을 쓰고 있고, 학점만 못 쓰고 있던 것이 문제였기 때문이다.
     */
    private static final double GPA_FLOOR_RATIO = 0.5;

    /**
     * 자격증 막대의 100% 기준이 되는 가중치 합. 학점이 4.5, 어학이 990이라는 고정 만점으로
     * 나누는 것과 같은 역할이다.
     *
     * 예전에는 "합격자 평균 × 1.5"로 나눴는데, 그러면 합격자 막대가 언제나
     * avgPasserCertValue / (avgPasserCertValue × 1.5) = 2/3, 즉 데이터와 무관하게 67%로 고정된다.
     * 합격자가 자격증을 평균 0.5개를 갖든 3개를 갖든 막대가 똑같아서, 데이터처럼 보이지만
     * 아무 정보도 담고 있지 않은 막대가 된다.
     *
     * 값 근거(2026-08 운영 DB 기준): 합격자 60명의 가중치 합 평균은 약 2.7이고,
     * 4.5는 "기사 1개(2.0) + SQLD(1.5) + ADsP(1.0)" 수준의 포트폴리오에 해당한다.
     * 합격자 평균이 대략 60% 지점에 오도록 잡은 값이므로, 합격자 데이터가 크게 바뀌면
     * 이 상수도 함께 재검토해야 한다.
     */
    private static final double CERT_SCALE_MAX = 4.5;

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
     * 자격증 가중치 판정에 필요한 문맥. "인식되는가"(observedCerts)와 "이 직무에서 얼마나
     * 중요한가"(jobWeights)는 서로 다른 질문이라 분리해서 들고 다닌다.
     *
     * hasJobData를 jobWeights.isEmpty()로 대신하지 않는 이유: "그 직무에 합격자 데이터 자체가
     * 없다"와 "합격자는 있는데 전원 자격증이 하나도 없다" 둘 다 jobWeights를 빈 맵으로 만들지만
     * 의미가 다르다. 전자는 큐레이션 표(CERT_WEIGHTS) 폴백이 맞지만, 후자는 실데이터가
     * "이 직무에서 자격증은 보유율 0%"라고 말하는 것이므로 하한(JOB_WEIGHT_MIN)을 써야 한다 —
     * 여기서 CERT_WEIGHTS로 빠지면 실데이터가 있는데도 임의 판단표를 쓰게 되어, 애초에
     * 보유율 기반 가중치를 도입한 이유(임의 판단 제거)가 이 케이스에서 무효화된다.
     */
    private record CertContext(Set<String> observedCerts, Map<String, Double> jobWeights, boolean hasJobData) {}

    /**
     * 유저 스펙과 합격자 케이스 목록을 비교하여 총점과 상세 내역(CompareRow)을 포함한 결과를 반환한다.
     *
     * @param globalCertPool 2층 인식에 쓸 자격증 원본 문자열 전체(직무·유사도 무관, DB 전체 스캔 결과).
     *                       비교 대상 passerList(Top N 유사 합격자)에서 뽑지 않는 이유:
     *                       Top N은 스펙을 살짝만 고쳐도, 합격자 데이터가 추가되기만 해도 바뀌는 값이라
     *                       그걸 인식 기준으로 쓰면 같은 자격증이 요청마다 인식됐다 안 됐다 흔들린다.
     *                       "이 자격증이 실재하는가"는 유사도와 무관한 질문이라 항상 같은 값이어야 한다.
     * @param jobPasserCertRows 목표 직무 합격자 <b>전원</b>의 자격증 배열(1인 1행, 자격증이 없으면 null 행).
     *                          가중치를 여기서 유도한다. globalCertPool과 같은 이유로 비교 대상 Top N이
     *                          아니라 해당 직무 전체를 넣어야 한다 — Top N에서 뽑으면 스펙을 조금만
     *                          고쳐도 가중치가 흔들려 점수가 요동친다. 비어 있으면 CERT_WEIGHTS로 폴백.
     */
    public MatchScoreResult calculate(UserSpec userSpec, List<PasserData> passerList,
                                      Set<String> globalCertPool, List<String[]> jobPasserCertRows) {
        // ⚠️ 직무 유도 가중치도 최소 표본을 요구한다(SimilarSpecFinder.MIN_SAMPLE과 같은 원칙).
        // 합격자가 1~2명뿐인 직무에선 그 한두 사람이 우연히 가진 자격증의 보유율이 100%가
        // 되어 가중치 상한(2.5)까지 치솟는다 — 예: 합격자 1명이 웹디자인기능사(큐레이션 0.5)를
        // 갖고 있으면 그 직무에선 웹디자인기능사(2.5)가 정보처리기사(2.0)보다 높게 평가되는
        // "이상한 결론"이 나왔다. 표본 미달이면 직무 데이터가 없는 것과 동일하게 큐레이션
        // 표(CERT_WEIGHTS)로 폴백한다.
        boolean hasJobData = jobPasserCertRows != null && jobPasserCertRows.size() >= MIN_JOB_WEIGHT_SAMPLE;
        CertContext certContext = new CertContext(
                collectObservedCerts(globalCertPool),
                hasJobData ? buildJobCertWeights(jobPasserCertRows) : Map.of(),
                hasJobData);

        if (userSpec == null) {
            return MatchScoreResult.builder()
                    .totalScore(0)
                    .compareRows(List.of())
                    .unrecognizedCertifications(List.of())
                    .recognizedCertificationCount(0)
                    .build();
        }

        // 미인식 자격증 고지는 유사 합격자 유무와 무관한 정보다 — 합격자가 0명이라 총점을
        // 못 내는 상황에서도 "이 자격증은 우리가 못 알아봤다"는 사실 자체는 알려줄 수 있다.
        List<String> unrecognized = unrecognizedCertifications(userSpec.getCertifications(), certContext);

        // ⚠️ 인식 개수는 여기서 직접 내려준다 — FE가 "입력 개수 − 미인식 개수"로 역산하면
        // 안 된다. 이 클래스의 인식·미인식 집계는 전부 canonicalCert 기준(공백·"필기/실기"
        // 등 제거 후 dedup)이라, "정보처리기사 필기"+"정보처리기사 실기"처럼 원본 2개가
        // canonical 1개로 접히는 순간 원본 개수 기반 뺄셈은 틀린 값이 된다(비교 탭 자격증
        // 행의 집계 — countRecognized — 와 어긋나는, 이 필드가 고치려는 바로 그 불일치).
        int recognizedCount = countRecognized(userSpec.getCertifications(), certContext);

        if (passerList == null || passerList.isEmpty()) {
            return MatchScoreResult.builder()
                    .totalScore(0)
                    .compareRows(List.of())
                    .unrecognizedCertifications(unrecognized)
                    .recognizedCertificationCount(recognizedCount)
                    .build();
        }

        double totalScore = calculateTotalScore(userSpec, passerList, certContext);

        List<CompareRowDto> rows = calculateDetails(userSpec, passerList, certContext);

        return MatchScoreResult.builder()
                .totalScore((int) Math.round(totalScore))
                .compareRows(rows)
                .unrecognizedCertifications(unrecognized)
                .recognizedCertificationCount(recognizedCount)
                .build();
    }

    private Set<String> collectObservedCerts(Set<String> globalCertPool) {
        if (globalCertPool == null) return Set.of();
        return globalCertPool.stream()
                .filter(Objects::nonNull)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * ⚠️ v7 재구성: 예전(~v6)에는 "합격자별로 3축 가중합 → 합격자 평균" 구조였고, 그 안에서
     * 합격자 쪽 데이터가 결측인 축은 자동 100점이었다(scoreGpa/scoreLang/scoreCert의
     * "비교 불가 → 감점 없음" 분기). 그런데 자동 100은 "감점 없음"이 아니라 "만점 가산"이다 —
     * 합격자 테이블에 결측 필드가 흔한 상황(운영 60행)에서 결측 많은 합격자가 뽑힐수록
     * 총점이 위로 부풀었고, 비교 탭 평균(calculateDetails)은 같은 결측을 "제외"로 처리해서
     * 화면의 막대(70~86%)와 총점(97점)이 서로 모순돼 보이는 "이상한 결론"이 나왔다
     * (2026-08-11 사용자 제보의 핵심 원인).
     *
     * v7은 축 단위로 계산한다: 각 축(학점·어학·자격증)마다 "그 축 데이터를 실제로 가진
     * 합격자"만 상대로 점수를 내 평균하고, 그 축 데이터를 가진 합격자가 한 명도 없으면 그
     * 축을 아예 제외하고 남은 축의 가중치로 재정규화한다. 이러면 결측은 점수를 올리지도
     * 내리지도 않는 진짜 "중립"이 되고, 총점과 비교 탭이 같은 결측 규칙을 쓰게 된다.
     * 전 축이 제외되면(합격자 전원이 전 필드 결측 — 사실상 불량 데이터) 0점을 반환한다.
     */
    private double calculateTotalScore(UserSpec userSpec, List<PasserData> passerList, CertContext certContext) {
        // 축별로 "데이터를 가진 합격자 대상 점수"들의 평균. 데이터 가진 합격자가 없으면 비어 있음.
        java.util.OptionalDouble gpaAxis = passerList.stream()
                .filter(p -> p != null && p.getGpa() != null && p.getGpaMax() != null && p.getGpaMax().signum() > 0)
                .mapToDouble(p -> scoreGpa(userSpec.getGpa(), userSpec.getGpaMax(), p.getGpa(), p.getGpaMax()))
                .average();
        java.util.OptionalDouble langAxis = passerList.stream()
                .filter(p -> p != null && getMaxEquivalentToeic(p.getLanguageScores()) > 0)
                .mapToDouble(p -> scoreLang(userSpec.getLanguageScores(), p.getLanguageScores()))
                .average();
        java.util.OptionalDouble certAxis = passerList.stream()
                .filter(p -> p != null && certValue(p.getCertifications(), certContext) > 0)
                .mapToDouble(p -> scoreCert(userSpec.getCertifications(), p.getCertifications(), certContext))
                .average();

        double weightedSum = 0.0;
        double weightUsed = 0.0;
        if (gpaAxis.isPresent())  { weightedSum += gpaAxis.getAsDouble()  * WEIGHT_GPA;  weightUsed += WEIGHT_GPA; }
        if (langAxis.isPresent()) { weightedSum += langAxis.getAsDouble() * WEIGHT_LANG; weightUsed += WEIGHT_LANG; }
        if (certAxis.isPresent()) { weightedSum += certAxis.getAsDouble() * WEIGHT_CERT; weightUsed += WEIGHT_CERT; }

        return weightUsed > 0 ? weightedSum / weightUsed : 0.0;
    }

    private List<CompareRowDto> calculateDetails(UserSpec userSpec, List<PasserData> passerList, CertContext certContext) {
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
        // myVal·avgVal은 "입력한 개수"가 아니라 "인식된(점수에 기여한) 개수"를 보여준다.
        // 입력 개수를 그대로 보여주면, 정크 5개를 넣었을 때 "5개"라고 뜨면서 동시에
        // 막대·상태는 0에 가깝게 나와 화면 안에서 숫자끼리 모순돼 보인다.
        // (실제로 뭘 놓쳤는지는 unrecognizedCertifications 배너가 별도로 알려준다.)
        int userCertCount = countRecognized(userSpec.getCertifications(), certContext);
        double avgPasserCertCount = passerList.stream()
                .mapToDouble(p -> countRecognized(p.getCertifications(), certContext))
                .average()
                .orElse(0.0);

        double userCertValue = certValue(userSpec.getCertifications(), certContext);
        double avgPasserCertValue = passerList.stream()
                .mapToDouble(p -> certValue(p.getCertifications(), certContext))
                .average()
                .orElse(0.0);

        // 막대는 학점(÷4.5)·어학(÷990)과 마찬가지로 고정 기준(CERT_SCALE_MAX)으로 나눈다.
        // 즉 "합격자 대비 몇 %"가 아니라 "자격증 항목을 절대적으로 얼마나 채웠나"를 나타낸다.
        // 합격자 평균으로 나누던 예전 방식은 합격자 막대를 항상 67%에 고정시켰다.
        //
        // 합격자 평균이 0이어도 별도 분기가 필요 없다 — 분모가 상수라 0으로 나눌 일이 없고,
        // "합격자도 자격증이 없다"를 0%로 보여주는 게 사실에 맞다. (예전엔 상대 기준이라
        // 0/0이 정의되지 않아 100%로 예외 처리했는데, 그건 자격증이 하나도 없는 사용자에게
        // "만점"이라고 표시하는 셈이라 오히려 오해를 만들었다. 자격증 항목이 감점되지 않는다는
        // 사실은 status의 "충족"과 총점이 이미 전달한다 — 학점·어학 막대가 낮아도 상대 비교에서
        // 충족이 나올 수 있는 것과 같은 구조다.)
        int certMyPct = (int) Math.min(100, Math.round(userCertValue / CERT_SCALE_MAX * 100));
        int certAvgPct = (int) Math.min(100, Math.round(avgPasserCertValue / CERT_SCALE_MAX * 100));

        CompareRowDto certRow = CompareRowDto.builder()
                .label("자격증/수상")
                .weight("26.7%")
                .myVal(String.format("%d개", userCertCount))
                .avgVal(String.format("%.1f개", avgPasserCertCount))
                .myPct(certMyPct)
                .avgPct(certAvgPct)
                .status(userCertValue >= avgPasserCertValue ? "충족" : "부족")
                .build();

        // ⚠️ 합격자 쪽 데이터가 없는 항목은 행 자체를 뺀다 — 총점(calculateTotalScore)이 그
        // 축을 제외·가중치 재정규화하는 것과 같은 규칙을 화면에도 적용한다. 예전엔 "양쪽 다
        // 0일 때만" 뺐는데, 그러면 사용자만 값이 있는 축(합격자 전원 결측)이 user >= avg(0)로
        // 무조건 "충족" + 명목 가중치("40%") 라벨을 달고 그려졌다 — 정작 총점 계산엔 그 축이
        // 아예 안 들어갔는데 화면은 "이 항목이 40%를 차지하고 충족"이라고 말하는, v7이 고친
        // 것과 같은 부류의 점수-막대 모순이다. 비교의 근거는 합격자 데이터이므로, 합격자
        // 데이터가 없으면 사용자 입력 여부와 무관하게 "비교 불가"로 행을 감춘다.
        // (사용자만 미입력이고 합격자 데이터는 있는 경우는 "부족"이 맞으므로 그대로 남는다.)
        //
        // 남은 행의 weight 라벨("40%" 등)은 명목값 그대로 둔다 — 재정규화는 비율을 보존하므로
        // (예: 어학 33.3% : 자격증 26.7% = 재정규화 후에도 5:4) 라벨 간 상대 비중은 계속 참이다.
        java.util.List<CompareRowDto> rows = new java.util.ArrayList<>(3);
        if (avgPasserGpaNorm > 0) rows.add(gpaRow);
        if (avgPasserToeic > 0) rows.add(langRow);
        if (avgPasserCertValue > 0) rows.add(certRow);
        return rows;
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
        // v7부터 합격자 쪽 학점 결측은 호출부(calculateTotalScore)가 필터로 "축 제외" 처리한다 —
        // 자동 100은 "감점 없음"이 아니라 "만점 가산"이라 총점을 부풀렸기 때문(버전 이력 7 참고).
        // 이 분기는 정상 경로에선 도달하지 않는 방어선으로만 남긴다.
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

        // 바닥값(GPA_FLOOR_RATIO)부터 합격자 기준선까지를 0~100으로 편다.
        // 단순 비율(userVal / passerVal)을 쓰면 학점 축만 하위 구간을 못 써서,
        // 명목 가중치 40%에 비해 실제 영향력이 훨씬 작아진다(GPA_FLOOR_RATIO 주석 참고).
        double span = passerVal - GPA_FLOOR_RATIO;
        if (span <= 0) {
            // 합격자 학점이 바닥값 이하라 구간을 만들 수 없다. 이 경우엔 바닥값을 적용하지 않고
            // 예전처럼 단순 비율로 비교한다(분모가 0 이하가 되는 것을 막는다).
            return Math.max(0.0, (userVal / passerVal) * 100.0);
        }
        return Math.max(0.0, (userVal - GPA_FLOOR_RATIO) / span * 100.0);
    }

    private double scoreLang(List<Map<String, Object>> userLangScores,
                             List<Map<String, Object>> passerLangScores) {
        // v7부터 합격자 어학 결측은 호출부가 필터로 "축 제외" 처리한다(scoreGpa 주석 참고).
        // 이 분기는 정상 경로에선 도달하지 않는 방어선으로만 남긴다.
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
                // "IM"은 세부등급(IM1~3) 미기재 표기 — 검증(@Pattern)은 통과하는데 이 환산표에
                // 없어서 조용히 0점(어학 미보유 취급)이 되던 값이다. 중간치인 IM2와 같게 본다.
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
    private double scoreCert(String[] userCerts, String[] passerCerts, CertContext certContext) {
        double passerValue = certValue(passerCerts, certContext);
        // v7부터 합격자 자격증 결측(가중치 합 0)은 호출부가 필터로 "축 제외" 처리한다
        // (scoreGpa 주석 참고). 이 분기는 정상 경로에선 도달하지 않는 방어선으로만 남긴다.
        if (passerValue <= 0) return 100.0;

        double userValue = certValue(userCerts, certContext);
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
    private double certValue(String[] certs, CertContext certContext) {
        if (certs == null) return 0.0;

        return Arrays.stream(certs)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .distinct()
                .mapToDouble(value -> weightOf(value, certContext))
                .sum();
    }

    /** 정규화 후 중복 제거된 자격증 중 실제로 인식된(가중치 > 0) 것의 개수. 화면 myVal·avgVal 표시용. */
    private int countRecognized(String[] certs, CertContext certContext) {
        if (certs == null) return 0;

        return (int) Arrays.stream(certs)
                .map(this::canonicalCert)
                .filter(value -> !value.isBlank())
                .distinct()
                .filter(value -> weightOf(value, certContext) > 0)
                .count();
    }

    /**
     * 목표 직무 합격자의 보유율에서 자격증별 가중치를 유도한다.
     *
     * 난이도에 대한 임의 판단(예전 CERT_WEIGHTS의 2.0/1.5/1.0/0.5) 대신
     * "그 직무 합격자 중 몇 %가 이 자격증을 갖고 있는가"를 쓴다. 점수의 정의가
     * "합격자 대비 준비도"이므로, 자격증의 난이도보다 그 직무에서의 실제 출현율이
     * 준비도 신호로 맞다. 실데이터가 쌓이면 코드 수정 없이 정확해지는 것도 장점이다.
     *
     * 분모는 "자격증을 가진 합격자 수"가 아니라 <b>해당 직무 합격자 전원</b>이다.
     * 자격증이 하나도 없는 합격자도 "그 자격증이 필수는 아니다"라는 정보를 담고 있어
     * 분모에서 빼면 보유율이 실제보다 부풀려진다.
     *
     * @param jobPasserCertRows 1인 1행. 자격증이 없는 합격자는 null 또는 빈 배열 행으로 들어온다.
     */
    private Map<String, Double> buildJobCertWeights(List<String[]> jobPasserCertRows) {
        if (jobPasserCertRows == null || jobPasserCertRows.isEmpty()) {
            return Map.of();
        }

        int totalPassers = jobPasserCertRows.size();
        Map<String, Integer> holderCounts = new LinkedHashMap<>();
        for (String[] row : jobPasserCertRows) {
            if (row == null) continue;
            // 한 사람이 같은 자격증을 중복 표기해도 1명으로 센다.
            Arrays.stream(row)
                    .map(this::canonicalCert)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .forEach(value -> holderCounts.merge(value, 1, Integer::sum));
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        holderCounts.forEach((cert, holders) -> {
            double holderRate = (double) holders / totalPassers;
            weights.put(cert, JOB_WEIGHT_MIN + (JOB_WEIGHT_MAX - JOB_WEIGHT_MIN) * holderRate);
        });
        return weights;
    }

    /**
     * 정규화된 자격증 표기 하나의 가중치를 판정한다.
     *
     * 인식 여부(3층)와 가중치는 분리된 질문이다.
     *   인식 — 1층 CERT_WEIGHTS / 2층 observedCerts(합격자 DB 실관측) / 3층 국가기술자격 종목명.
     *          세 층 어디에도 없으면 0이다. v3부터 지켜온 "미인식 입력은 정확히 0" 원칙이라,
     *          정크 입력이 실재하는 자격증과 같은 취급을 받지 않게 하는 핵심 불변식이다.
     *   가중치 — 목표 직무 합격자의 보유율에서 유도(jobWeights). 직무 데이터가 없으면
     *          기존 큐레이션 표로 폴백한다.
     */
    private double weightOf(String canonicalCert, CertContext certContext) {
        boolean recognized = CERT_WEIGHTS.containsKey(canonicalCert)
                || NATIONAL_TECH_CERTIFICATIONS.contains(canonicalCert)
                || certContext.observedCerts().contains(canonicalCert);
        if (!recognized) {
            return 0.0;
        }

        // hasJobData로 폴백 여부를 가른다 — jobWeights.isEmpty()로 판단하면 "직무 데이터가
        // 아예 없다"와 "직무 합격자는 있는데 전원 자격증이 0개다"가 똑같이 빈 맵이 되어
        // 후자도 CERT_WEIGHTS로 잘못 폴백한다(그 직무에서 보유율 0%라는 실데이터가 있는데도
        // 임의 판단표를 쓰게 됨 — 애초에 보유율 기반으로 바꾼 이유가 무효화된다).
        if (certContext.hasJobData()) {
            // 그 직무 합격자 중 아무도 갖고 있지 않은 자격증도 "인식은 되므로" 하한을 받는다.
            return certContext.jobWeights().getOrDefault(canonicalCert, JOB_WEIGHT_MIN);
        }

        // 직무 미설정이거나 해당 직무에 합격자 데이터가 없을 때의 폴백.
        return CERT_WEIGHTS.getOrDefault(canonicalCert, RECOGNIZED_DEFAULT_WEIGHT);
    }

    /**
     * 사용자가 입력한 자격증 중 세 층 어디에서도 인식하지 못한 항목을 원본 표기 그대로 반환한다.
     * 화면에 고지해 사용자가 오타·특이 표기를 스스로 고칠 수 있게 하기 위함이다 — 조용히
     * 버리기만 하면 "왜 이건 반영 안 됐지?"에 코드를 보지 않고는 답할 수 없다.
     */
    private List<String> unrecognizedCertifications(String[] userCerts, CertContext certContext) {
        if (userCerts == null) return List.of();

        // 정규화하면 같아지는 표기(공백·괄호 차이 등)를 원본 문자열 기준으로만 distinct하면
        // "정크자격", "정크자격 ", "정크자격()"처럼 사람 눈엔 같은 값이 여러 번 나열된다.
        // canonical 기준으로 중복을 제거하되, 화면엔 처음 등장한 원본 표기를 그대로 보여준다.
        Map<String, String> firstRawByCanonical = new LinkedHashMap<>();
        for (String raw : userCerts) {
            if (raw == null || raw.isBlank()) continue;
            String canonical = canonicalCert(raw);
            if (canonical.isBlank() || weightOf(canonical, certContext) > 0) continue;
            firstRawByCanonical.putIfAbsent(canonical, raw);
        }
        return List.copyOf(firstRawByCanonical.values());
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
