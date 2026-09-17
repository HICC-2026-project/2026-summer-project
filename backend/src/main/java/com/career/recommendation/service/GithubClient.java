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
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
    /**
     * WebClient 기본 maxInMemorySize(256KB)로는 /search/commits?per_page=100나 큰 레포의
     * /git/trees?recursive=1 응답(수백 KB)을 파싱하다 DataBufferLimitException이 난다 — 운영
     * 로그에는 "status=200"으로 찍혀 마치 정상 응답인데 실패한 것처럼 보였다(실제로는 예외가
     * HTTP 상태와 무관하게 버퍼 크기 초과로 발생). 10MB면 사실상 모든 응답을 수용한다.
     */
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    /** 커밋 검색 후보 상한(30)이면 50건으로 충분하고, 100 대비 응답 크기를 절반으로 줄인다. */
    private static final int COMMIT_SEARCH_PER_PAGE = 50;

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
        Clients clients = buildClients();
        WebClient client = clients.authClient();
        WebClient anonymousClient = clients.anonymousClient();

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

        List<Map<String, Object>> ownedRepos;
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
            ownedRepos = body != null ? body : List.of();
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) {
                return GithubAnalysisRawResult.rateLimited(List.of());
            }
            throw e;
        }

        int maxRepos = hasToken() ? maxReposWithToken : maxReposWithoutToken;

        // 조직 레포 등 "소유하지 않은" 기여 레포 발견 — 비공개 멤버십이면 /users/{u}/orgs가
        // 비어 있어도 커밋 검색은 여전히 그 사람의 커밋이 있는 레포를 찾아낸다. 검색 API는
        // 별도 쿼터(코어와 무관)이고 실패해도 전체 분석을 막지 않는다 — 아래에서 조용히 넘어간다.
        List<Map<String, Object>> discoveredRepos =
                discoverContributedRepos(client, anonymousClient, username, ownedRepos, maxRepos);

        List<Map<String, Object>> merged = new ArrayList<>(ownedRepos);
        merged.addAll(discoveredRepos);

        List<Map<String, Object>> filtered = merged.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("fork")) && !Boolean.TRUE.equals(r.get("archived")))
                .sorted(Comparator.comparing(
                        (Map<String, Object> r) -> String.valueOf(r.getOrDefault("pushed_at", "")),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxRepos)
                .toList();

        List<RepoRawData> results = new ArrayList<>();
        boolean rateLimited = false;
        for (Map<String, Object> repo : filtered) {
            String repoOwnerLogin = ownerLoginOf(repo, username);
            RepoFetchOutcome outcome = fetchRepoData(client, anonymousClient, repoOwnerLogin, username, repo);
            if (outcome.rateLimited()) {
                rateLimited = true;
                break;
            }
            results.add(outcome.data());
        }
        return new GithubAnalysisRawResult(results, rateLimited);
    }

    /**
     * GET /search/commits?q=author:{u}로 이 사람이 커밋한 레포(조직 소유 포함)를 찾는다.
     * 비공개 조직 멤버십이면 /users/{u}/orgs는 빈 배열을 돌려주지만, 커밋 검색은 author 필터로
     * 실제 기여 레포를 찾아낸다(공개 레포에 한함 — 검색 API 자체가 비공개 레포는 접근 권한
     * 없이는 찾지 못한다). 검색 1회만 호출하고, 실패(403/429 등 별도 쿼터 소진 포함)해도
     * 예외를 던지지 않고 빈 목록을 돌려준다 — 소유 레포만으로 분석을 계속 진행하기 위함이다.
     */
    private List<Map<String, Object>> discoverContributedRepos(
            WebClient client, WebClient anonymousClient, String username,
            List<Map<String, Object>> ownedRepos, int maxCandidates) {
        List<Map<String, Object>> discovered = new ArrayList<>();
        try {
            Set<String> ownedFullNames = ownedRepos.stream()
                    .map(r -> String.valueOf(r.get("full_name")))
                    .collect(java.util.stream.Collectors.toSet());

            ResponseEntity<Map> searchResponse = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/search/commits")
                            .queryParam("q", "author:" + username)
                            .queryParam("sort", "committer-date")
                            .queryParam("order", "desc")
                            .queryParam("per_page", COMMIT_SEARCH_PER_PAGE)
                            .build())
                    .retrieve()
                    .toEntity(Map.class)
                    .block(CALL_TIMEOUT);

            Object items = searchResponse != null && searchResponse.getBody() != null
                    ? searchResponse.getBody().get("items")
                    : null;
            if (!(items instanceof List<?> itemList)) {
                return discovered;
            }

            // committer-date desc 순서를 그대로 유지해, 후보가 상한보다 많을 때 최근 기여 레포가
            // 먼저 메타데이터 조회 대상이 되게 한다.
            java.util.LinkedHashSet<String> candidateFullNames = new java.util.LinkedHashSet<>();
            for (Object item : itemList) {
                if (!(item instanceof Map<?, ?> itemMap)) continue;
                Object repoObj = itemMap.get("repository");
                if (!(repoObj instanceof Map<?, ?> repoMap)) continue;
                if (Boolean.TRUE.equals(repoMap.get("fork"))) continue; // 1차 필터(검색 응답 자체 필드)
                Object fullNameObj = repoMap.get("full_name");
                if (fullNameObj == null) continue;
                String fullName = String.valueOf(fullNameObj);
                if (fullName.isBlank() || ownedFullNames.contains(fullName)) continue; // 이미 소유 목록에 있음
                candidateFullNames.add(fullName);
            }

            int checked = 0;
            for (String fullName : candidateFullNames) {
                if (checked >= maxCandidates) break; // 코어 쿼터 보호 — 상한만큼만 메타데이터 조회
                checked++;
                String[] parts = fullName.split("/", 2);
                if (parts.length != 2) continue;
                Map<String, Object> meta = fetchRepoMeta(client, anonymousClient, parts[0], parts[1]);
                if (meta == null) continue; // 조회 실패(권한 없음·삭제됨·코어 레이트리밋 등) — 조용히 건너뜀
                if (Boolean.TRUE.equals(meta.get("archived")) || Boolean.TRUE.equals(meta.get("fork"))) continue;
                discovered.add(meta);
            }
        } catch (Exception e) {
            // 검색 API는 코어와 별도 쿼터(무토큰 10/min)라 403/429가 흔할 수 있다 — 전체 분석을
            // 실패시키지 않고 소유 레포만으로 계속 진행한다.
            logSkip("GitHub 커밋 검색", "author:" + username, e);
        }
        return discovered;
    }

    /** 검색으로 찾은 레포의 archived·fork·default_branch·pushed_at 등 전체 메타데이터를 확인한다. */
    private Map<String, Object> fetchRepoMeta(WebClient client, WebClient anonymousClient, String ownerLogin, String repoName) {
        String fullName = ownerLogin + "/" + repoName;
        try {
            ResponseEntity<Map> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri("/repos/{owner}/{repo}", ownerLogin, repoName)
                            .retrieve()
                            .toEntity(Map.class)
                            .block(CALL_TIMEOUT));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = resp != null ? (Map<String, Object>) resp.getBody() : null;
            return body;
        } catch (Exception e) {
            logSkip("GitHub 레포 메타데이터 조회 (레포 발견 건너뜀)", fullName, e);
            return null;
        }
    }

    /** repos 목록·검색 메타데이터 모두 "owner.login" 필드를 갖는다. 없으면 분석 대상 계정으로 가정. */
    private static String ownerLoginOf(Map<String, Object> repo, String fallbackUsername) {
        Object owner = repo.get("owner");
        if (owner instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
            return String.valueOf(ownerMap.get("login"));
        }
        return fallbackUsername;
    }

    private RepoFetchOutcome fetchRepoData(
            WebClient client, WebClient anonymousClient, String repoOwnerLogin, String authorUsername, Map<String, Object> repoMeta) {
        String repoName = String.valueOf(repoMeta.get("name"));
        String fullName = repoOwnerLogin + "/" + repoName;
        String description = repoMeta.get("description") != null ? String.valueOf(repoMeta.get("description")) : null;
        String defaultBranch = repoMeta.get("default_branch") != null
                ? String.valueOf(repoMeta.get("default_branch"))
                : "main";

        Map<String, Long> languages = new LinkedHashMap<>();
        try {
            ResponseEntity<Map> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri("/repos/{owner}/{repo}/languages", repoOwnerLogin, repoName)
                            .retrieve()
                            .toEntity(Map.class)
                            .block(CALL_TIMEOUT));
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
            logSkip("GitHub languages 조회 (무시)", fullName, e);
        }

        int commitCount = 0;
        LocalDate first = null;
        LocalDate last = null;
        try {
            ResponseEntity<List> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/commits")
                                    .queryParam("author", authorUsername)
                                    .queryParam("per_page", commitsPerRepo)
                                    .build(repoOwnerLogin, repoName))
                            .retrieve()
                            .toEntity(List.class)
                            .block(CALL_TIMEOUT));
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
                logSkip("GitHub commits 조회 (0건 취급)", fullName, e);
            }
        }

        List<String> filePaths = new ArrayList<>();
        try {
            ResponseEntity<Map> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri(uriBuilder -> uriBuilder.path("/repos/{owner}/{repo}/git/trees/{branch}")
                                    .queryParam("recursive", 1)
                                    .build(repoOwnerLogin, repoName, defaultBranch))
                            .retrieve()
                            .toEntity(Map.class)
                            .block(CALL_TIMEOUT));
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
            logSkip("GitHub tree 조회 (빈 파일 목록 취급)", fullName, e);
        }

        List<String> dependencies = new ArrayList<>();
        Set<String> rootFiles = filePaths.stream()
                .filter(p -> !p.contains("/"))
                .map(p -> p.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String depFileName : DEPENDENCY_FILE_NAMES) {
            if (!rootFiles.contains(depFileName.toLowerCase(Locale.ROOT))) continue;
            try {
                ResponseEntity<Map> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                        c -> c.get()
                                .uri("/repos/{owner}/{repo}/contents/{path}", repoOwnerLogin, repoName, depFileName)
                                .retrieve()
                                .toEntity(Map.class)
                                .block(CALL_TIMEOUT));
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
                logSkip("GitHub 의존성 파일 조회 (건너뜀)", fullName + "/" + depFileName, e);
            } catch (Exception e) {
                log.debug("GitHub 의존성 파일 파싱 실패 (건너뜀): {}/{}", fullName, depFileName);
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

    /** 인증 클라이언트와, 조직의 fine-grained PAT 차단 시 폴백용 익명 클라이언트를 함께 만든다. */
    private record Clients(WebClient authClient, WebClient anonymousClient) {}

    private Clients buildClients() {
        // 기본 256KB로는 /search/commits·/git/trees의 큰 응답을 못 받아 DataBufferLimitException이
        // 났다(위 MAX_IN_MEMORY_SIZE_BYTES 주석 참고) — 인증·익명 클라이언트 둘 다 적용해야 한다.
        ExchangeStrategies largeBufferStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                .build();
        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl)
                .exchangeStrategies(largeBufferStrategies)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        WebClient anonymous = builder.build();
        WebClient authenticated = hasToken()
                // 토큰 원문은 여기서만 쓰이고 로그에는 절대 남기지 않는다. 인증 클라이언트는 별도로
                // clone()한 빌더에만 Authorization을 추가해, anonymous 빌드에 영향을 주지 않는다.
                ? builder.clone().defaultHeader("Authorization", "Bearer " + token).build()
                : anonymous;
        return new Clients(authenticated, anonymous);
    }

    /**
     * 레포 단위 GET(메타·languages·commits·트리·contents) 공통 헬퍼. 토큰이 설정된 상태에서
     * 403을 받으면 같은 요청을 Authorization 헤더 없는 익명 클라이언트로 1회만 재시도한다 —
     * fine-grained PAT를 차단하는 조직의 공개 레포는 익명 요청이 오히려 성공하기 때문이다.
     * 익명 재시도도 실패하면 그 예외를 그대로 던져 호출부의 기존 실패 처리(레이트리밋 판정/
     * 건너뜀)를 그대로 태운다.
     */
    private <T> ResponseEntity<T> requestWithAnonymousFallback(
            WebClient authClient, WebClient anonymousClient, String targetFullName,
            java.util.function.Function<WebClient, ResponseEntity<T>> request) {
        try {
            return request.apply(authClient);
        } catch (WebClientResponseException e) {
            if (authClient != anonymousClient && e.getStatusCode().value() == 403) {
                log.warn("조직이 토큰을 차단해 익명으로 재시도: {}", targetFullName);
                return request.apply(anonymousClient);
            }
            throw e;
        }
    }

    /**
     * 실패를 조용히 건너뛰는 지점의 로그를 운영(INFO)에서도 보이도록 warn으로 남긴다. 상태코드·대상만
     * 남기고 토큰 등 민감정보는 남기지 않는다.
     *
     * ⚠️ 예전엔 WebClientResponseException이 아닌 예외(DataBufferLimitException 등)도
     * "status=200"처럼 실제 HTTP 응답 상태만 보이는 형태로 찍혀, 마치 정상 응답을 받고도 실패한
     * 것처럼 오독됐다(운영에서 실측: "GitHub 커밋 검색 실패: author:... - status=200"). 예외
     * 클래스명을 항상 함께 남기고, status는 실제로 HTTP 응답에서 온 WebClientResponseException일
     * 때만 붙인다.
     */
    private void logSkip(String action, String targetFullName, Exception e) {
        if (e instanceof WebClientResponseException wcre) {
            log.warn("{} 실패: {} - {} status={}", action, targetFullName,
                    e.getClass().getSimpleName(), wcre.getStatusCode().value());
        } else {
            log.warn("{} 실패: {} - {} {}", action, targetFullName, e.getClass().getSimpleName(), e.getMessage());
        }
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
