package com.career.recommendation.util;

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

    /** 레포 하나의 분류 결과. primaryJob==null이면 OTHER. */
    public record RepoClassification(
            String repoName,
            JobType primaryJob,
            int fileCount,
            int commitCount,
            int activeMonths,
            String mainLanguage
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

    private static final Set<String> LOCK_FILE_NAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json",
            "composer.lock", "poetry.lock", "cargo.lock", "gemfile.lock"
    );
    private static final Set<String> NOISE_DIR_TOKENS = Set.of("dist", "build", "node_modules", "vendor");

    /** 동점 시 앞쪽 직무가 이긴다(결정적 규칙). PM은 코드 신호로 식별하지 않으므로 목록에 없다. */
    private static final List<JobType> PRIORITY_ORDER =
            List.of(JobType.BACKEND, JobType.FRONTEND, JobType.SECURITY, JobType.AI_ML, JobType.DATA_ENGINEER);

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

        return new RepoClassification(repo.name(), primary, cleanPaths.size(), repo.commitCount(), repo.activeMonths(), mainLanguage);
    }

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
     * 레포별 primaryJob에 "커밋 수 40% + 파일 수 30% + 활동 개월 30%" 가중을 실어 사용자 전체
     * 직무 비율을 만든다. 결과는 항상 JobType 6종 + OTHER 7개 키를 갖는다(0이어도 포함 —
     * 화면 막대 차트가 모든 직무를 일관되게 그릴 수 있게).
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

    private static boolean hasExtension(List<String> paths, Set<String> extensions) {
        return countExtension(paths, extensions) > 0;
    }

    private static long countExtension(List<String> paths, String extension) {
        return countExtension(paths, Set.of(extension));
    }

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

    private static boolean containsAny(String corpus, Set<String> keywords) {
        for (String kw : keywords) {
            if (corpus.contains(kw)) return true;
        }
        return false;
    }

    /** 경로 세그먼트를 구분자·camelCase 경계로 토큰화한 뒤, 토큰과 정확히 일치할 때만 매칭한다. */
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

    private static void add(Map<JobType, Double> score, JobType jobType, double weight) {
        score.merge(jobType, weight, Double::sum);
    }
}
