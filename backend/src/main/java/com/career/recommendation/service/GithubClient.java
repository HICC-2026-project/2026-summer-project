package com.career.recommendation.service;

import com.career.recommendation.exception.GithubOrganizationAccountException;
import com.career.recommendation.exception.GithubUserNotFoundException;
import com.career.recommendation.util.JobSignalClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * E3(1단계) — GitHub 공개 API 호출. GeminiService와 같은 스택(WebClient.Builder를 매 호출 새로
 * baseUrl에 물려 build)·스타일(Map/List 원시 파싱, 상세 예외 메시지를 로그에만)을 따른다.
 *
 * 토큰(github.api.token)은 절대 로그에 남기지 않는다 — 설정되면 Authorization 헤더로만 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubClient {

    @Value("${github.api.base-url:https://api.github.com}")
    private String baseUrl;

    @Value("${github.api.token:}")
    private String token;

    @Value("${github.api.max-repos-with-token:30}")
    private int maxReposWithToken;

    @Value("${github.api.max-repos-without-token:10}")
    private int maxReposWithoutToken;

    @Value("${github.api.commits-per-repo:50}")
    private int commitsPerRepo;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    /** 응답의 X-RateLimit-Remaining이 이 값 미만이면 그 즉시 분석을 중단한다. */
    private static final int RATE_LIMIT_STOP_THRESHOLD = 5;

    /** 트리에 있을 때만 조회하는 루트 의존성 파일. 순서는 우선순위와 무관 — 존재하는 것만 조회. */
    private static final List<String> DEPENDENCY_FILE_NAMES =
            List.of("package.json", "pom.xml", "build.gradle", "build.gradle.kts", "requirements.txt", "pyproject.toml");

    // --- 결과 타입 ---

    /** 레포 하나의 원시 수집 결과 — JobSignalClassifier 입력으로 변환하기 전 단계. */
    public record RepoRawData(
            String name,
            String description,
            Map<String, Long> languageBytes,
            List<String> filePaths,
            List<String> dependencies,
            int commitCount,
            LocalDate firstCommitAt,
            LocalDate lastCommitAt
    ) {
        public JobSignalClassifier.RepoSignal toSignal() {
            int activeMonths = computeActiveMonths(firstCommitAt, lastCommitAt, commitCount);
            return new JobSignalClassifier.RepoSignal(
                    name, description, languageBytes, filePaths, dependencies, commitCount, activeMonths);
        }
    }

    /** analyze() 최종 결과. rateLimited면 repos는 그때까지 모은 부분 결과. */
    public record GithubAnalysisRawResult(List<RepoRawData> repos, boolean rateLimited) {
        public static GithubAnalysisRawResult rateLimited(List<RepoRawData> partial) {
            return new GithubAnalysisRawResult(partial, true);
        }
    }

    /** 레포 하나 수집 도중 레이트리밋에 걸렸는지 여부를 함께 실어 나르는 내부 결과. */
    private record RepoFetchOutcome(RepoRawData data, boolean rateLimited) {
        static RepoFetchOutcome blocked() {
            return new RepoFetchOutcome(null, true);
        }

        static RepoFetchOutcome of(RepoRawData data) {
            return new RepoFetchOutcome(data, false);
        }
    }

    /**
     * username 계정의 공개 레포를 분석한다.
     *
     * @throws GithubUserNotFoundException      GET /users/{u}가 404
     * @throws GithubOrganizationAccountException 대상이 조직(Organization) 계정
     */
    public GithubAnalysisRawResult analyze(String username) {
        WebClient client = buildClient();

        ResponseEntity<Map> userResponse;
        try {
            userResponse = client.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .toEntity(Map.class)
                    .block(CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new GithubUserNotFoundException(username);
            }
            throw e;
        }

        Map<?, ?> user = userResponse != null ? userResponse.getBody() : null;
        if (user != null && "Organization".equalsIgnoreCase(String.valueOf(user.get("type")))) {
            throw new GithubOrganizationAccountException(username);
        }
        if (userResponse != null && isRateLimited(userResponse.getHeaders(), userResponse.getStatusCode())) {
            return GithubAnalysisRawResult.rateLimited(List.of());
        }

        List<Map<String, Object>> repos;
        try {
            ResponseEntity<List> reposResponse = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/users/{username}/repos")
                            .queryParam("per_page", 100)
                            .queryParam("sort", "pushed")
                            .queryParam("type", "owner")
                            .build(username))
                    .retrieve()
                    .toEntity(List.class)
                    .block(CALL_TIMEOUT);
            if (reposResponse != null && isRateLimited(reposResponse.getHeaders(), reposResponse.getStatusCode())) {
                return GithubAnalysisRawResult.rateLimited(List.of());
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = reposResponse != null
                    ? (List<Map<String, Object>>) (List<?>) reposResponse.getBody()
                    : List.of();
            repos = body != null ? body : List.of();
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) {
                return GithubAnalysisRawResult.rateLimited(List.of());
            }
            throw e;
        }

        int maxRepos = hasToken() ? maxReposWithToken : maxReposWithoutToken;
        List<Map<String, Object>> filtered = repos.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("fork")) && !Boolean.TRUE.equals(r.get("archived")))
                .sorted(Comparator.comparing(
                        (Map<String, Object> r) -> String.valueOf(r.getOrDefault("pushed_at", "")),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxRepos)
                .toList();

        List<RepoRawData> results = new ArrayList<>();
        boolean rateLimited = false;
        for (Map<String, Object> repo : filtered) {
            RepoFetchOutcome outcome = fetchRepoData(client, username, repo);
            if (outcome.rateLimited()) {
                rateLimited = true;
                break;
            }
            results.add(outcome.data());
        }
        return new GithubAnalysisRawResult(results, rateLimited);
    }

    private RepoFetchOutcome fetchRepoData(WebClient client, String owner, Map<String, Object> repoMeta) {
        String repoName = String.valueOf(repoMeta.get("name"));
        String description = repoMeta.get("description") != null ? String.valueOf(repoMeta.get("description")) : null;
        String defaultBranch = repoMeta.get("default_branch") != null
                ? String.valueOf(repoMeta.get("default_branch"))
                : "main";

        Map<String, Long> languages = new LinkedHashMap<>();
        try {
            ResponseEntity<Map> resp = client.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repoName)
                    .retrieve()
                    .toEntity(Map.class)
                    .block(CALL_TIMEOUT);
            if (resp != null && isRateLimited(resp.getHeaders(), resp.getStatusCode())) {
                return RepoFetchOutcome.blocked();
            }
            Map<?, ?> body = resp != null ? resp.getBody() : null;
            if (body != null) {
                for (Map.Entry<?, ?> e : body.entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        languages.put(String.valueOf(e.getKey()), n.longValue());
                    }
                }
            }
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) return RepoFetchOutcome.blocked();
            log.debug("GitHub languages 조회 실패 (무시): {}/{} - {}", owner, repoName, e.getStatusCode());
        }

        int commitCount = 0;
        LocalDate first = null;
        LocalDate last = null;
        try {
            ResponseEntity<List> resp = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/commits")
                            .queryParam("author", owner)
                            .queryParam("per_page", commitsPerRepo)
                            .build(owner, repoName))
                    .retrieve()
                    .toEntity(List.class)
                    .block(CALL_TIMEOUT);
            if (resp != null && isRateLimited(resp.getHeaders(), resp.getStatusCode())) {
                return RepoFetchOutcome.blocked();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commits = resp != null ? (List<Map<String, Object>>) (List<?>) resp.getBody() : null;
            if (commits != null) {
                commitCount = commits.size();
                for (Map<String, Object> commit : commits) {
                    LocalDate date = extractCommitDate(commit);
                    if (date != null) {
                        if (first == null || date.isBefore(first)) first = date;
                        if (last == null || date.isAfter(last)) last = date;
                    }
                }
            }
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) {
                return RepoFetchOutcome.blocked();
            }
            if (e.getStatusCode().value() != 409) {
                // 409 = 빈 레포(커밋 없음) — 정상 케이스로 0건 유지. 그 외는 로그만 남기고 0건 취급.
                log.debug("GitHub commits 조회 실패 (0건 취급): {}/{} - {}", owner, repoName, e.getStatusCode());
            }
        }

        List<String> filePaths = new ArrayList<>();
        try {
            ResponseEntity<Map> resp = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/git/trees/{branch}")
                            .queryParam("recursive", 1)
                            .build(owner, repoName, defaultBranch))
                    .retrieve()
                    .toEntity(Map.class)
                    .block(CALL_TIMEOUT);
            if (resp != null && isRateLimited(resp.getHeaders(), resp.getStatusCode())) {
                return RepoFetchOutcome.blocked();
            }
            Object treeObj = resp != null && resp.getBody() != null ? resp.getBody().get("tree") : null;
            if (treeObj instanceof List<?> tree) {
                for (Object entry : tree) {
                    if (entry instanceof Map<?, ?> node && "blob".equals(String.valueOf(node.get("type")))) {
                        Object path = node.get("path");
                        if (path != null) filePaths.add(String.valueOf(path));
                    }
                }
            }
            // truncated=true는 그대로 허용 — 지금까지 받은 경로 목록만으로 신호를 계산한다.
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) return RepoFetchOutcome.blocked();
            log.debug("GitHub tree 조회 실패 (빈 파일 목록 취급): {}/{} - {}", owner, repoName, e.getStatusCode());
        }

        List<String> dependencies = new ArrayList<>();
        Set<String> rootFiles = filePaths.stream()
                .filter(p -> !p.contains("/"))
                .map(p -> p.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String depFileName : DEPENDENCY_FILE_NAMES) {
            if (!rootFiles.contains(depFileName.toLowerCase(Locale.ROOT))) continue;
            try {
                ResponseEntity<Map> resp = client.get()
                        .uri("/repos/{owner}/{repo}/contents/{path}", owner, repoName, depFileName)
                        .retrieve()
                        .toEntity(Map.class)
                        .block(CALL_TIMEOUT);
                if (resp != null && isRateLimited(resp.getHeaders(), resp.getStatusCode())) {
                    return RepoFetchOutcome.blocked();
                }
                Map<?, ?> body = resp != null ? resp.getBody() : null;
                if (body != null && "base64".equals(body.get("encoding")) && body.get("content") != null) {
                    String decoded = decodeBase64(String.valueOf(body.get("content")));
                    dependencies.addAll(extractDependencies(depFileName, decoded));
                }
            } catch (WebClientResponseException e) {
                if (isRateLimitStatus(e)) return RepoFetchOutcome.blocked();
                log.debug("GitHub 의존성 파일 조회 실패 (건너뜀): {}/{}/{} - {}", owner, repoName, depFileName, e.getStatusCode());
            } catch (Exception e) {
                log.debug("GitHub 의존성 파일 파싱 실패 (건너뜀): {}/{}/{}", owner, repoName, depFileName);
            }
        }

        return RepoFetchOutcome.of(new RepoRawData(repoName, description, languages, filePaths, dependencies, commitCount, first, last));
    }

    // --- 레이트리밋 판정 ---

    private boolean isRateLimitStatus(WebClientResponseException e) {
        HttpHeaders headers = e.getHeaders();
        if (e.getStatusCode().value() == 403 && headers.getFirst("Retry-After") != null) {
            return true;
        }
        return isRateLimited(headers, e.getStatusCode());
    }

    private boolean isRateLimited(HttpHeaders headers, HttpStatusCode status) {
        if (status != null && status.value() == 403 && headers.getFirst("Retry-After") != null) {
            return true;
        }
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            try {
                return Integer.parseInt(remaining.trim()) < RATE_LIMIT_STOP_THRESHOLD;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    // --- 클라이언트 빌드 ---

    private boolean hasToken() {
        return token != null && !token.isBlank();
    }

    private WebClient buildClient() {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (hasToken()) {
            // 토큰 원문은 여기서만 쓰이고 로그에는 절대 남기지 않는다.
            builder = builder.defaultHeader("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    // --- 파싱 헬퍼 ---

    private LocalDate extractCommitDate(Map<String, Object> commit) {
        Object commitObj = commit.get("commit");
        if (!(commitObj instanceof Map<?, ?> c)) return null;
        String dateStr = extractDateField(c.get("author"));
        if (dateStr == null) {
            dateStr = extractDateField(c.get("committer"));
        }
        if (dateStr == null) return null;
        try {
            return OffsetDateTime.parse(dateStr).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractDateField(Object authorOrCommitter) {
        if (authorOrCommitter instanceof Map<?, ?> m && m.get("date") != null) {
            return String.valueOf(m.get("date"));
        }
        return null;
    }

    private static String decodeBase64(String content) {
        String cleaned = content.replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(cleaned);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 파일 종류별로 의존성 "이름"만 뽑아낸다 — 버전·정확한 파서 트리는 필요 없다(키워드 매칭용). */
    private List<String> extractDependencies(String fileName, String content) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.equals("package.json")) {
                return extractFromPackageJson(content);
            }
            if (lower.equals("pom.xml")) {
                return extractFromPomXml(content);
            }
            if (lower.equals("build.gradle") || lower.equals("build.gradle.kts")) {
                return extractFromGradle(content);
            }
            if (lower.equals("requirements.txt")) {
                return extractFromRequirementsTxt(content);
            }
            if (lower.equals("pyproject.toml")) {
                return extractFromPyprojectToml(content);
            }
        } catch (Exception e) {
            log.debug("의존성 파일 파싱 실패({}): {}", fileName, e.getMessage());
        }
        return List.of();
    }

    private List<String> extractFromPackageJson(String content) throws Exception {
        List<String> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(content);
        for (String field : List.of("dependencies", "devDependencies")) {
            JsonNode node = root.get(field);
            if (node != null && node.isObject()) {
                node.fieldNames().forEachRemaining(result::add);
            }
        }
        return result;
    }

    private static final java.util.regex.Pattern POM_ARTIFACT_PATTERN =
            java.util.regex.Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");

    private List<String> extractFromPomXml(String content) {
        List<String> result = new ArrayList<>();
        var matcher = POM_ARTIFACT_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static final java.util.regex.Pattern GRADLE_DEP_PATTERN = java.util.regex.Pattern.compile(
            "(?:implementation|api|compile|testImplementation|runtimeOnly|annotationProcessor)\\s*[\\(]?\\s*['\"]([^'\"]+)['\"]");

    private List<String> extractFromGradle(String content) {
        List<String> result = new ArrayList<>();
        var matcher = GRADLE_DEP_PATTERN.matcher(content);
        while (matcher.find()) {
            String coordinate = matcher.group(1); // group:artifact:version
            String[] parts = coordinate.split(":");
            result.add(parts.length >= 2 ? parts[1] : coordinate);
        }
        return result;
    }

    private List<String> extractFromRequirementsTxt(String content) {
        List<String> result = new ArrayList<>();
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) continue;
            String name = trimmed.split("[<>=!~;\\[\\s]")[0].trim();
            if (!name.isEmpty()) result.add(name);
        }
        return result;
    }

    private static final java.util.regex.Pattern PYPROJECT_DEP_PATTERN =
            java.util.regex.Pattern.compile("\"([A-Za-z][A-Za-z0-9_.\\-]*)(?:[<>=!~ ][^\"]*)?\"");

    private List<String> extractFromPyprojectToml(String content) {
        List<String> result = new ArrayList<>();
        var matcher = PYPROJECT_DEP_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    static int computeActiveMonths(LocalDate first, LocalDate last, int commitCount) {
        if (commitCount <= 0 || first == null || last == null) return 0;
        long months = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), last.withDayOfMonth(1));
        return (int) Math.max(1, months + 1);
    }
}
