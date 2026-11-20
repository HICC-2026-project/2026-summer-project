package com.career.recommendation.util;

import com.career.recommendation.domain.ExperienceArea;
import com.career.recommendation.domain.JobType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * E3(1단계) — GitHub 공개 레포 신호(언어 바이트·파일 경로·의존성·레포명/설명·커밋 수·활동 개월)로
 * 레포별 주 직무(JobType 6종 중 하나 또는 OTHER)를 정하고, 사용자 전체 직무 비율을 계산하는
 * 결정적(비-Gemini) 규칙. Gemini에 의존하지 않는다 — GapMatcher와 같은 성격의 유틸.
 *
 * 짧은 키워드의 부분문자열 오매칭(GapMatcher가 ai·ml·ui·pm에서 겪은 문제와 같은 종류)을 막기 위해,
 * 경로 기반 키워드는 전체 문자열 substring이 아니라 경로 세그먼트를 (구분자 + camelCase 경계로)
 * 토큰화한 뒤 토큰 단위로 정확히 일치할 때만 매칭한다 — 예: "capital-gains"의 세그먼트를
 * 토큰화하면 ["capital","gains"]가 되어 "api"와 절대 부분일치하지 않는다.
 */
public final class JobSignalClassifier {

    private JobSignalClassifier() {
    }

    /** 레포 하나의 원시 신호. GithubClient가 채워서 넘긴다. */
    public record RepoSignal(
            String name,
            String description,
            Map<String, Long> languageBytes,
            List<String> filePaths,
            List<String> dependencies,
            int commitCount,
            int activeMonths
    ) {
    }

    /**
     * 레포 하나의 분류 결과. primaryJob==null이면 OTHER.
     *
     * areas: E11(1단계) — 레포 신호로 도출한 기여 영역(최대 5개, 신호 강한 순). PLANNING은
     * 코드 신호로 식별하지 않으므로 여기 담기지 않는다.
     * stack: 의존성 파일에서 뽑은 라이브러리 이름 상위 5개. 의존성이 없으면 주 언어 1개.
     */
    public record RepoClassification(
            String repoName,
            JobType primaryJob,
            int fileCount,
            int commitCount,
            int activeMonths,
            String mainLanguage,
            List<String> areas,
            List<String> stack
    ) {
    }

    /** repos: 레포별 분류 결과. userRatios: JobType.name() 6종 + "OTHER" 키, 합 1.0(레포가 있을 때). */
    public record ClassificationResult(
            List<RepoClassification> repos,
            Map<String, Double> userRatios
    ) {
    }

    private static final String OTHER = "OTHER";

    // 점수 가중치 — 서로 다른 신호 종류(언어 바이트 유무·경로 토큰·의존성·확장자)를 같은 잣대로
    // 섞을 수 없으므로, 신호 "종류"별로 고정된 투표 점수를 부여하는 방식을 쓴다(바이트 크기 자체는
    // 쓰지 않는다 — 레포마다 스케일이 달라 왜곡이 크다).
    private static final double LANGUAGE_WEIGHT = 3.0;
    private static final double EXTENSION_WEIGHT = 3.0;
    private static final double PATH_TOKEN_WEIGHT = 2.0;
    private static final double DEPENDENCY_WEIGHT = 2.0;
    private static final double SECURITY_CONFIG_FILE_WEIGHT = 2.0;
    private static final double SECURITY_NAME_KEYWORD_WEIGHT = 3.0;
    /** 인프라 신호는 별도 직무가 아니라 BACKEND 보조 가중(×0.5) — PATH_TOKEN_WEIGHT의 절반. */
    private static final double INFRA_WEIGHT = PATH_TOKEN_WEIGHT * 0.5;
    private static final int DATA_SQL_FILE_THRESHOLD = 5;

    private static final Set<String> BACKEND_LANGUAGES = Set.of("java", "kotlin", "go", "ruby", "php");
    private static final Set<String> PY_BACKEND_FRAMEWORK_DEPS = Set.of("django", "fastapi", "flask");
    private static final Set<String> BACKEND_PATH_TOKENS = Set.of("controller", "service", "repository", "domain", "api");
    private static final Set<String> BACKEND_DEPENDENCY_TOKENS =
            Set.of("spring-boot", "spring", "express", "nest", "nestjs", "django", "fastapi", "flask");
    private static final Set<String> INFRA_PATH_TOKENS =
            Set.of("terraform", "k8s", "helm", "nginx", "dockerfile", "workflows");
    private static final Set<String> INFRA_DEPENDENCY_TOKENS = Set.of("terraform", "k8s", "helm", "nginx");

    private static final Set<String> FRONTEND_EXTENSIONS = Set.of("tsx", "jsx", "vue", "svelte", "css", "scss", "html");
    private static final Set<String> FRONTEND_PATH_TOKENS = Set.of("components", "pages", "app", "hooks", "styles");
    private static final Set<String> FRONTEND_DEPENDENCY_TOKENS =
            Set.of("react", "next", "vue", "svelte", "tailwind", "tailwindcss");

    private static final Set<String> SECURITY_PATH_TOKENS = Set.of("security", "auth", "jwt", "oauth", "crypto", "acl");
    private static final Set<String> SECURITY_DEPENDENCY_TOKENS =
            Set.of("spring-security", "passport", "bcrypt", "jose", "keycloak");
    private static final Set<String> SECURITY_NAME_KEYWORDS = Set.of("ctf", "exploit", "pwn", "vuln");
    private static final String SECURITY_CONFIG_FILE_PREFIX = "securityconfig";

    private static final Set<String> AI_DEPENDENCY_TOKENS =
            Set.of("torch", "pytorch", "tensorflow", "sklearn", "scikit-learn", "transformers", "langchain");
    private static final Set<String> AI_PATH_TOKENS = Set.of("models", "notebooks", "training");

    private static final Set<String> DATA_DEPENDENCY_TOKENS = Set.of("airflow", "dbt", "spark", "kafka", "flink");
    private static final Set<String> DATA_PATH_TOKENS = Set.of("pipelines", "etl", "dags", "warehouse");

    // --- ExperienceArea(기여 영역) 전용 신호 — JobType 신호와 겹치는 것은 위 상수를 재사용한다 ---
    private static final Set<String> AREA_API_PATH_TOKENS = Set.of("controller", "api", "service", "repository", "domain");
    private static final Set<String> AREA_DB_PATH_TOKENS = Set.of("db", "entity", "repository", "migration");
    private static final Set<String> AREA_DB_DEPENDENCY_TOKENS = Set.of("jpa", "hibernate", "prisma", "typeorm", "mybatis");
    private static final String AREA_CI_CD_PATH_PREFIX = ".github/workflows/";
    private static final Set<String> AREA_INFRA_PATH_TOKENS = Set.of("terraform", "k8s", "helm", "nginx", "dockerfile");
    private static final String AREA_DOCKER_COMPOSE_PREFIX = "docker-compose";
    private static final Set<String> AREA_UI_EXTENSIONS = Set.of("tsx", "jsx", "vue", "svelte", "css", "scss");
    private static final Set<String> AREA_UI_PATH_TOKENS = Set.of("components", "pages", "styles");
    private static final Set<String> AREA_STATE_DEPENDENCY_TOKENS = Set.of("redux", "zustand", "recoil", "mobx", "pinia");
    private static final Set<String> AREA_STATE_PATH_TOKENS = Set.of("store", "state");
    private static final double DOCS_MD_RATIO_THRESHOLD = 0.5;
    private static final int DOCS_PATH_COUNT_THRESHOLD = 3;
    private static final int MAX_AREAS_PER_REPO = 5;
    private static final int MAX_STACK_PER_REPO = 5;

    private static final Set<String> LOCK_FILE_NAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json",
            "composer.lock", "poetry.lock", "cargo.lock", "gemfile.lock"
    );
    private static final Set<String> NOISE_DIR_TOKENS = Set.of("dist", "build", "node_modules", "vendor");

    /** 동점 시 앞쪽 직무가 이긴다(결정적 규칙). PM은 코드 신호로 식별하지 않으므로 목록에 없다. */
    private static final List<JobType> PRIORITY_ORDER =
            List.of(JobType.BACKEND, JobType.FRONTEND, JobType.SECURITY, JobType.AI_ML, JobType.DATA_ENGINEER);

    /**
     * 함수 이름: classify()
     * 기능: 레포별 신호를 각각 분류하고, 그 결과를 합산해 사용자 전체 직무 비율을 만든다.
     * 파라미터: List<RepoSignal> repoSignals -> 레포별 원시 신호 목록(null·빈 목록 허용)
     * 반환값: 레포별 분류 결과와 직무 비율. 입력이 비어 있으면 빈 목록·빈 맵을 담은 결과
     */
    public static ClassificationResult classify(List<RepoSignal> repoSignals) {
        if (repoSignals == null || repoSignals.isEmpty()) {
            return new ClassificationResult(List.of(), Map.of());
        }

        List<RepoClassification> classifications = new ArrayList<>();
        for (RepoSignal repo : repoSignals) {
            classifications.add(classifyRepo(repo));
        }
        return new ClassificationResult(classifications, aggregateUserRatios(classifications));
    }

    /**
     * 함수 이름: classifyRepo()
     * 기능: 레포 하나의 신호(언어·경로·의존성·이름/설명)에 신호 종류별 고정 가중치를 매겨 직무별 점수를
     *       계산하고, 최고점 직무를 주 직무로 정한다. 기여 영역과 기술 스택도 함께 도출한다.
     *       노이즈 경로(락 파일·빌드 산출물 등)는 먼저 걸러내며, 점수가 모두 0이면 주 직무는 null(OTHER)이다.
     * 파라미터: RepoSignal repo -> 분류할 레포의 원시 신호
     * 반환값: 주 직무·파일 수·커밋 수·활동 개월·주 언어·기여 영역·스택을 담은 분류 결과
     */
    public static RepoClassification classifyRepo(RepoSignal repo) {
        List<String> cleanPaths = filterNoise(repo.filePaths());
        List<String> deps = repo.dependencies() == null ? List.of() : repo.dependencies();
        Map<String, Long> langs = repo.languageBytes() == null ? Map.of() : repo.languageBytes();
        String nameDesc = ((repo.name() == null ? "" : repo.name()) + " " + (repo.description() == null ? "" : repo.description()))
                .toLowerCase(Locale.ROOT);

        EnumMap<JobType, Double> score = new EnumMap<>(JobType.class);
        for (JobType jt : JobType.values()) {
            score.put(jt, 0.0);
        }

        boolean hasBackendLang = langs.keySet().stream()
                .anyMatch(l -> l != null && BACKEND_LANGUAGES.contains(l.toLowerCase(Locale.ROOT)));
        boolean hasPythonWebDep = langs.keySet().stream().anyMatch(l -> l != null && l.equalsIgnoreCase("python"))
                && hasDependency(deps, PY_BACKEND_FRAMEWORK_DEPS);
        if (hasBackendLang || hasPythonWebDep) {
            add(score, JobType.BACKEND, LANGUAGE_WEIGHT);
        }
        if (pathHasToken(cleanPaths, BACKEND_PATH_TOKENS)) {
            add(score, JobType.BACKEND, PATH_TOKEN_WEIGHT);
        }
        if (hasDependency(deps, BACKEND_DEPENDENCY_TOKENS)) {
            add(score, JobType.BACKEND, DEPENDENCY_WEIGHT);
        }
        if (pathHasToken(cleanPaths, INFRA_PATH_TOKENS) || hasDependency(deps, INFRA_DEPENDENCY_TOKENS)) {
            add(score, JobType.BACKEND, INFRA_WEIGHT);
        }

        if (hasExtension(cleanPaths, FRONTEND_EXTENSIONS)) {
            add(score, JobType.FRONTEND, EXTENSION_WEIGHT);
        }
        if (pathHasToken(cleanPaths, FRONTEND_PATH_TOKENS)) {
            add(score, JobType.FRONTEND, PATH_TOKEN_WEIGHT);
        }
        if (hasDependency(deps, FRONTEND_DEPENDENCY_TOKENS)) {
            add(score, JobType.FRONTEND, DEPENDENCY_WEIGHT);
        }

        if (pathHasToken(cleanPaths, SECURITY_PATH_TOKENS)) {
            add(score, JobType.SECURITY, PATH_TOKEN_WEIGHT);
        }
        if (hasDependency(deps, SECURITY_DEPENDENCY_TOKENS)) {
            add(score, JobType.SECURITY, DEPENDENCY_WEIGHT);
        }
        if (hasFilenamePrefix(cleanPaths, SECURITY_CONFIG_FILE_PREFIX)) {
            add(score, JobType.SECURITY, SECURITY_CONFIG_FILE_WEIGHT);
        }
        if (containsAny(nameDesc, SECURITY_NAME_KEYWORDS)) {
            add(score, JobType.SECURITY, SECURITY_NAME_KEYWORD_WEIGHT);
        }

        if (hasExtension(cleanPaths, Set.of("ipynb"))) {
            add(score, JobType.AI_ML, EXTENSION_WEIGHT);
        }
        if (hasDependency(deps, AI_DEPENDENCY_TOKENS)) {
            add(score, JobType.AI_ML, DEPENDENCY_WEIGHT);
        }
        if (pathHasToken(cleanPaths, AI_PATH_TOKENS)) {
            add(score, JobType.AI_ML, PATH_TOKEN_WEIGHT);
        }

        if (hasDependency(deps, DATA_DEPENDENCY_TOKENS)) {
            add(score, JobType.DATA_ENGINEER, DEPENDENCY_WEIGHT);
        }
        if (pathHasToken(cleanPaths, DATA_PATH_TOKENS)) {
            add(score, JobType.DATA_ENGINEER, PATH_TOKEN_WEIGHT);
        }
        if (countExtension(cleanPaths, "sql") >= DATA_SQL_FILE_THRESHOLD) {
            add(score, JobType.DATA_ENGINEER, DEPENDENCY_WEIGHT);
        }

        JobType primary = pickPrimary(score);
        String mainLanguage = langs.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        List<String> areas = deriveAreas(cleanPaths, deps, nameDesc);
        List<String> stack = extractStack(deps, mainLanguage);

        return new RepoClassification(
                repo.name(), primary, cleanPaths.size(), repo.commitCount(), repo.activeMonths(),
                mainLanguage, areas, stack);
    }

    // --- ExperienceArea(기여 영역) 도출 ---

    /**
     * 함수 이름: deriveAreas()
     * 기능: 레포 신호로 기여 영역을 최대 {@value #MAX_AREAS_PER_REPO}개까지 신호 강한 순으로 뽑는다.
     *       영역별로 경로·의존성·확장자 신호에 가중치를 더하고, 점수가 0보다 큰 영역만 점수 내림차순
     *       (동점이면 enum 선언 순)으로 고른다.
     *       PLANNING은 코드 신호로 식별하지 않으므로 절대 담기지 않는다.
     * 파라미터: List<String> cleanPaths -> 노이즈를 걸러낸 파일 경로 목록
     *           List<String> deps -> 의존성 이름 목록
     *           String nameDesc -> 레포명과 설명을 합친 소문자 문자열
     * 반환값: 기여 영역 이름(ExperienceArea.name()) 목록
     */
    private static List<String> deriveAreas(List<String> cleanPaths, List<String> deps, String nameDesc) {
        EnumMap<ExperienceArea, Double> score = new EnumMap<>(ExperienceArea.class);

        // AUTH — SECURITY_PATH_TOKENS 등 JobType.SECURITY와 같은 신호를 재사용한다.
        scoreIf(score, pathHasToken(cleanPaths, SECURITY_PATH_TOKENS), ExperienceArea.AUTH, PATH_TOKEN_WEIGHT);
        scoreIf(score, hasDependency(deps, SECURITY_DEPENDENCY_TOKENS), ExperienceArea.AUTH, DEPENDENCY_WEIGHT);
        scoreIf(score, hasFilenamePrefix(cleanPaths, SECURITY_CONFIG_FILE_PREFIX), ExperienceArea.AUTH, SECURITY_CONFIG_FILE_WEIGHT);

        // API
        scoreIf(score, pathHasToken(cleanPaths, AREA_API_PATH_TOKENS), ExperienceArea.API, PATH_TOKEN_WEIGHT);

        // DB
        scoreIf(score, pathHasToken(cleanPaths, AREA_DB_PATH_TOKENS), ExperienceArea.DB, PATH_TOKEN_WEIGHT);
        scoreIf(score, countExtension(cleanPaths, "sql") > 0, ExperienceArea.DB, EXTENSION_WEIGHT);
        scoreIf(score, hasDependency(deps, AREA_DB_DEPENDENCY_TOKENS), ExperienceArea.DB, DEPENDENCY_WEIGHT);

        // CI_CD
        scoreIf(score, hasPathPrefix(cleanPaths, AREA_CI_CD_PATH_PREFIX), ExperienceArea.CI_CD, PATH_TOKEN_WEIGHT);

        // INFRA
        scoreIf(score, pathHasToken(cleanPaths, AREA_INFRA_PATH_TOKENS), ExperienceArea.INFRA, PATH_TOKEN_WEIGHT);
        scoreIf(score, hasFilenamePrefix(cleanPaths, AREA_DOCKER_COMPOSE_PREFIX), ExperienceArea.INFRA, PATH_TOKEN_WEIGHT);
        scoreIf(score, hasDependency(deps, INFRA_DEPENDENCY_TOKENS), ExperienceArea.INFRA, DEPENDENCY_WEIGHT);

        // TEST
        scoreIf(score, hasTestPathSignal(cleanPaths), ExperienceArea.TEST, PATH_TOKEN_WEIGHT);
        scoreIf(score, hasTestFileSignal(cleanPaths), ExperienceArea.TEST, EXTENSION_WEIGHT);

        // UI
        scoreIf(score, hasExtension(cleanPaths, AREA_UI_EXTENSIONS), ExperienceArea.UI, EXTENSION_WEIGHT);
        scoreIf(score, pathHasToken(cleanPaths, AREA_UI_PATH_TOKENS), ExperienceArea.UI, PATH_TOKEN_WEIGHT);

        // STATE_MGMT
        scoreIf(score, hasDependency(deps, AREA_STATE_DEPENDENCY_TOKENS), ExperienceArea.STATE_MGMT, DEPENDENCY_WEIGHT);
        scoreIf(score, pathHasToken(cleanPaths, AREA_STATE_PATH_TOKENS), ExperienceArea.STATE_MGMT, PATH_TOKEN_WEIGHT);

        // DATA_PIPELINE — JobType.DATA_ENGINEER와 같은 신호를 재사용한다.
        scoreIf(score, hasDependency(deps, DATA_DEPENDENCY_TOKENS), ExperienceArea.DATA_PIPELINE, DEPENDENCY_WEIGHT);
        scoreIf(score, pathHasToken(cleanPaths, DATA_PATH_TOKENS), ExperienceArea.DATA_PIPELINE, PATH_TOKEN_WEIGHT);

        // ML_MODEL — JobType.AI_ML과 같은 신호를 재사용한다.
        scoreIf(score, hasExtension(cleanPaths, Set.of("ipynb")), ExperienceArea.ML_MODEL, EXTENSION_WEIGHT);
        scoreIf(score, hasDependency(deps, AI_DEPENDENCY_TOKENS), ExperienceArea.ML_MODEL, DEPENDENCY_WEIGHT);
        scoreIf(score, pathHasToken(cleanPaths, AI_PATH_TOKENS), ExperienceArea.ML_MODEL, PATH_TOKEN_WEIGHT);

        // DOCS
        scoreIf(score, hasDocsSignal(cleanPaths), ExperienceArea.DOCS, PATH_TOKEN_WEIGHT);

        // SECURITY(보안 분석) — repo 이름/설명의 CTF류 키워드. AUTH의 보안 "경로" 신호와는 별개다.
        scoreIf(score, containsAny(nameDesc, SECURITY_NAME_KEYWORDS), ExperienceArea.SECURITY, SECURITY_NAME_KEYWORD_WEIGHT);

        return score.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<ExperienceArea, Double>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().ordinal()))
                .limit(MAX_AREAS_PER_REPO)
                .map(e -> e.getKey().name())
                .toList();
    }

    /**
     * 함수 이름: scoreIf()
     * 기능: 조건이 참일 때만 해당 기여 영역의 점수에 가중치를 누적한다.
     * 파라미터: Map<ExperienceArea, Double> score -> 영역별 점수 맵(갱신됨)
     *           boolean condition -> 신호 존재 여부
     *           ExperienceArea area -> 점수를 더할 영역
     *           double weight -> 더할 가중치
     * 반환값: 없음
     */
    private static void scoreIf(Map<ExperienceArea, Double> score, boolean condition, ExperienceArea area, double weight) {
        if (condition) {
            score.merge(area, weight, Double::sum);
        }
    }

    /**
     * 함수 이름: hasPathPrefix()
     * 기능: 경로 중 소문자로 바꿨을 때 주어진 접두사로 시작하는 것이 하나라도 있는지 검사한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           String prefixLower -> 소문자 접두사
     * 반환값: 접두사로 시작하는 경로가 있으면 true, 없으면 false
     */
    private static boolean hasPathPrefix(List<String> paths, String prefixLower) {
        for (String path : paths) {
            if (path.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 함수 이름: hasTestPathSignal()
     * 기능: 경로에 테스트 디렉터리 표식("src/test" 또는 "__tests__")이 있는지 검사한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     * 반환값: 테스트 디렉터리 경로가 하나라도 있으면 true, 없으면 false
     */
    private static boolean hasTestPathSignal(List<String> paths) {
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains("src/test") || lower.contains("__tests__")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 함수 이름: hasTestFileSignal()
     * 기능: 파일명에 테스트 파일 표식(".test." 또는 ".spec.")이 있는지 검사한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     * 반환값: 테스트 파일이 하나라도 있으면 true, 없으면 false
     */
    private static boolean hasTestFileSignal(List<String> paths) {
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            String fileName = lower.substring(lower.lastIndexOf('/') + 1);
            if (fileName.contains(".test.") || fileName.contains(".spec.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 함수 이름: hasDocsSignal()
     * 기능: 마크다운 파일 비중이 50% 이상이거나 docs/ 경로 파일이 3개 이상이면 문서화 신호로 본다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     * 반환값: 문서화 신호가 있으면 true. 경로 목록이 비어 있으면 false
     */
    private static boolean hasDocsSignal(List<String> paths) {
        if (paths.isEmpty()) {
            return false;
        }
        long mdCount = countExtension(paths, "md");
        double ratio = (double) mdCount / paths.size();
        long docsPathCount = paths.stream().filter(p -> {
            for (String seg : p.toLowerCase(Locale.ROOT).split("/")) {
                if (seg.equals("docs")) {
                    return true;
                }
            }
            return false;
        }).count();
        return ratio >= DOCS_MD_RATIO_THRESHOLD || docsPathCount >= DOCS_PATH_COUNT_THRESHOLD;
    }

    /**
     * 함수 이름: extractStack()
     * 기능: 의존성 이름 상위 5개(중복 제거)를 스택으로 뽑는다. 의존성이 하나도 없으면 주 언어 1개라도 담는다.
     * 파라미터: List<String> deps -> 의존성 이름 목록(null 허용)
     *           String mainLanguage -> 주 언어(null 허용)
     * 반환값: 기술 스택 목록. 의존성도 주 언어도 없으면 빈 리스트
     */
    private static List<String> extractStack(List<String> deps, String mainLanguage) {
        if (deps != null && !deps.isEmpty()) {
            List<String> cleaned = deps.stream()
                    .filter(d -> d != null && !d.isBlank())
                    .distinct()
                    .limit(MAX_STACK_PER_REPO)
                    .toList();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return mainLanguage != null ? List.of(mainLanguage) : List.of();
    }

    /**
     * 함수 이름: pickPrimary()
     * 기능: 직무별 점수 중 최고점 직무를 주 직무로 고른다. PRIORITY_ORDER 순서로 훑으며 엄격히 큰
     *       점수만 갱신하므로 동점이면 앞쪽 직무가 이긴다. 모든 점수가 0이면 null(OTHER)이다.
     * 파라미터: Map<JobType, Double> score -> 직무별 점수
     * 반환값: 주 직무. 해당 없으면 null
     */
    private static JobType pickPrimary(Map<JobType, Double> score) {
        JobType primary = null;
        double max = 0.0;
        for (JobType jt : PRIORITY_ORDER) {
            double s = score.getOrDefault(jt, 0.0);
            if (s > max) {
                max = s;
                primary = jt;
            }
        }
        return primary; // null == OTHER
    }

    /**
     * 함수 이름: aggregateUserRatios()
     * 기능: 레포별 primaryJob에 "커밋 수 40% + 파일 수 30% + 활동 개월 30%" 가중을 실어 사용자 전체
     *       직무 비율을 만든다. 합계가 0보다 크면 합이 1.0이 되도록 정규화한다. 결과는 항상
     *       JobType 6종 + OTHER 7개 키를 갖는다(0이어도 포함 — 화면 막대 차트가 모든 직무를
     *       일관되게 그릴 수 있게).
     * 파라미터: List<RepoClassification> repos -> 레포별 분류 결과(null·빈 목록 허용)
     * 반환값: 직무 이름(OTHER 포함) -> 비율 맵. 레포가 없으면 모두 0.0
     */
    public static Map<String, Double> aggregateUserRatios(List<RepoClassification> repos) {
        Map<String, Double> ratios = new LinkedHashMap<>();
        for (JobType jt : JobType.values()) {
            ratios.put(jt.name(), 0.0);
        }
        ratios.put(OTHER, 0.0);

        if (repos == null || repos.isEmpty()) {
            return ratios;
        }

        double totalCommits = repos.stream().mapToInt(RepoClassification::commitCount).sum();
        double totalFiles = repos.stream().mapToInt(RepoClassification::fileCount).sum();
        double totalMonths = repos.stream().mapToInt(RepoClassification::activeMonths).sum();
        int n = repos.size();

        for (RepoClassification repo : repos) {
            double commitShare = totalCommits > 0 ? repo.commitCount() / totalCommits : 1.0 / n;
            double fileShare = totalFiles > 0 ? repo.fileCount() / totalFiles : 1.0 / n;
            double monthShare = totalMonths > 0 ? repo.activeMonths() / totalMonths : 1.0 / n;
            double weight = 0.4 * commitShare + 0.3 * fileShare + 0.3 * monthShare;

            String bucket = repo.primaryJob() == null ? OTHER : repo.primaryJob().name();
            ratios.merge(bucket, weight, Double::sum);
        }

        double sum = ratios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            ratios.replaceAll((k, v) -> v / sum);
        }
        return ratios;
    }

    // --- 노이즈 필터 ---

    /**
     * 함수 이름: filterNoise()
     * 기능: 경로 목록에서 null과 노이즈 경로(락 파일·압축본·빌드/의존성 디렉터리)를 제거한다.
     * 파라미터: List<String> paths -> 파일 경로 목록(null 허용)
     * 반환값: 노이즈를 걸러낸 경로 목록. 입력이 null이면 빈 리스트
     */
    private static List<String> filterNoise(List<String> paths) {
        if (paths == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String path : paths) {
            if (path != null && !isNoise(path)) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * 함수 이름: isNoise()
     * 기능: 경로가 분류에 방해되는 노이즈인지 판단한다. 락 파일, ".min." 압축 파일, dist·build·
     *       node_modules·vendor 디렉터리 아래 파일이 노이즈다.
     * 파라미터: String path -> 검사할 파일 경로
     * 반환값: 노이즈이면 true, 아니면 false
     */
    private static boolean isNoise(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (LOCK_FILE_NAMES.contains(fileName)) {
            return true;
        }
        if (fileName.contains(".min.")) {
            return true;
        }
        for (String seg : lower.split("/")) {
            if (NOISE_DIR_TOKENS.contains(seg)) {
                return true;
            }
        }
        return false;
    }

    // --- 신호 매칭 ---

    /**
     * 함수 이름: hasDependency()
     * 기능: 의존성 이름(소문자) 중 키워드를 부분문자열로 포함하는 것이 하나라도 있는지 검사한다.
     * 파라미터: List<String> dependencies -> 의존성 이름 목록(null·빈 목록 허용)
     *           Set<String> keywords -> 찾을 소문자 키워드 집합
     * 반환값: 일치하는 의존성이 있으면 true, 없으면 false
     */
    private static boolean hasDependency(List<String> dependencies, Set<String> keywords) {
        if (dependencies == null || dependencies.isEmpty()) return false;
        for (String dep : dependencies) {
            if (dep == null) continue;
            String lower = dep.toLowerCase(Locale.ROOT);
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
        }
        return false;
    }

    /**
     * 함수 이름: hasExtension()
     * 기능: 주어진 확장자 집합에 해당하는 파일이 하나라도 있는지 검사한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           Set<String> extensions -> 소문자 확장자 집합(점 제외)
     * 반환값: 해당 확장자 파일이 있으면 true, 없으면 false
     */
    private static boolean hasExtension(List<String> paths, Set<String> extensions) {
        return countExtension(paths, extensions) > 0;
    }

    /**
     * 함수 이름: countExtension()
     * 기능: 단일 확장자에 해당하는 파일 개수를 센다(집합 버전에 위임).
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           String extension -> 소문자 확장자(점 제외)
     * 반환값: 해당 확장자 파일 수
     */
    private static long countExtension(List<String> paths, String extension) {
        return countExtension(paths, Set.of(extension));
    }

    /**
     * 함수 이름: countExtension()
     * 기능: 마지막 점 뒤의 확장자(소문자)가 주어진 집합에 속하는 파일 개수를 센다.
     *       점이 없거나 점으로 끝나는 경로는 건너뛴다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           Set<String> extensions -> 소문자 확장자 집합(점 제외)
     * 반환값: 해당 확장자 파일 수
     */
    private static long countExtension(List<String> paths, Set<String> extensions) {
        long count = 0;
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            if (dot < 0 || dot == lower.length() - 1) continue;
            String ext = lower.substring(dot + 1);
            if (extensions.contains(ext)) count++;
        }
        return count;
    }

    /**
     * 함수 이름: hasFilenamePrefix()
     * 기능: 파일명에서 확장자를 뺀 이름(stem)이 주어진 접두사로 시작하는 파일이 있는지 검사한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           String prefixLower -> 소문자 접두사
     * 반환값: 일치하는 파일이 있으면 true, 없으면 false
     */
    private static boolean hasFilenamePrefix(List<String> paths, String prefixLower) {
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            String fileName = lower.substring(lower.lastIndexOf('/') + 1);
            int dot = fileName.lastIndexOf('.');
            String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
            if (stem.startsWith(prefixLower)) return true;
        }
        return false;
    }

    /**
     * 함수 이름: containsAny()
     * 기능: 본문에 키워드 집합 중 하나라도 부분문자열로 들어 있는지 검사한다.
     * 파라미터: String corpus -> 검색 대상 문자열(소문자)
     *           Set<String> keywords -> 찾을 키워드 집합
     * 반환값: 하나라도 포함되면 true, 없으면 false
     */
    private static boolean containsAny(String corpus, Set<String> keywords) {
        for (String kw : keywords) {
            if (corpus.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 함수 이름: pathHasToken()
     * 기능: 경로 세그먼트를 구분자·camelCase 경계로 토큰화한 뒤, 토큰과 정확히 일치할 때만 매칭한다.
     * 파라미터: List<String> paths -> 파일 경로 목록
     *           Set<String> tokens -> 찾을 소문자 토큰 집합
     * 반환값: 일치하는 토큰이 하나라도 있으면 true, 없으면 false
     */
    private static boolean pathHasToken(List<String> paths, Set<String> tokens) {
        for (String path : paths) {
            for (String segment : path.split("/")) {
                for (String token : tokenize(segment)) {
                    if (tokens.contains(token)) return true;
                }
            }
        }
        return false;
    }

    /**
     * 함수 이름: tokenize()
     * 기능: 경로 세그먼트에서 확장자를 떼고, camelCase 경계와 영숫자가 아닌 문자를 기준으로 잘라
     *       소문자 토큰 목록을 만든다. 예: "capital-gains" -> ["capital", "gains"].
     * 파라미터: String segment -> 경로의 한 세그먼트
     * 반환값: 소문자 토큰 목록(빈 토큰 제외)
     */
    private static List<String> tokenize(String segment) {
        String base = segment;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        // camelCase 경계에 공백 삽입 (lower->Upper 전환 지점)
        String withBoundaries = base.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        String[] parts = withBoundaries.split("[^A-Za-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    /**
     * 함수 이름: add()
     * 기능: 직무별 점수 맵에서 해당 직무의 점수에 가중치를 누적한다.
     * 파라미터: Map<JobType, Double> score -> 직무별 점수 맵(갱신됨)
     *           JobType jobType -> 점수를 더할 직무
     *           double weight -> 더할 가중치
     * 반환값: 없음
     */
    private static void add(Map<JobType, Double> score, JobType jobType, double weight) {
        score.merge(jobType, weight, Double::sum);
    }
}
