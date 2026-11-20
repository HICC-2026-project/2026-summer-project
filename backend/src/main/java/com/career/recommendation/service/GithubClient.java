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
        /**
         * 함수 이름: toSignal()
         * 기능: 레포 원시 수집 결과를 JobSignalClassifier 입력(RepoSignal)으로 변환한다. 활동 개월 수는 첫/마지막 커밋 날짜로 계산한다.
         * 파라미터: 없음
         * 반환값: JobSignalClassifier.RepoSignal -> 직무 신호 분류기에 넘길 레포 신호
         */
        public JobSignalClassifier.RepoSignal toSignal() {
            int activeMonths = computeActiveMonths(firstCommitAt, lastCommitAt, commitCount);
            return new JobSignalClassifier.RepoSignal(
                    name, description, languageBytes, filePaths, dependencies, commitCount, activeMonths);
        }
    }

    /** analyze() 최종 결과. rateLimited면 repos는 그때까지 모은 부분 결과. */
    public record GithubAnalysisRawResult(List<RepoRawData> repos, boolean rateLimited) {
        /**
         * 함수 이름: rateLimited()
         * 기능: 레이트리밋에 걸려 중단된 분석 결과를 만드는 정적 팩토리. 그때까지 모은 부분 결과를 담고 rateLimited=true로 표시한다.
         * 파라미터: List<RepoRawData> partial -> 레이트리밋 전까지 수집된 레포 목록
         * 반환값: GithubAnalysisRawResult -> rateLimited가 true인 분석 결과
         */
        public static GithubAnalysisRawResult rateLimited(List<RepoRawData> partial) {
            return new GithubAnalysisRawResult(partial, true);
        }
    }

    /** 레포 하나 수집 도중 레이트리밋에 걸렸는지 여부를 함께 실어 나르는 내부 결과. */
    private record RepoFetchOutcome(RepoRawData data, boolean rateLimited) {
        /**
         * 함수 이름: blocked()
         * 기능: 레이트리밋에 막혀 데이터 없이 중단됐음을 나타내는 결과를 만든다.
         * 파라미터: 없음
         * 반환값: RepoFetchOutcome -> data=null, rateLimited=true인 결과
         */
        static RepoFetchOutcome blocked() {
            return new RepoFetchOutcome(null, true);
        }

        /**
         * 함수 이름: of()
         * 기능: 정상 수집된 레포 데이터를 rateLimited=false 결과로 감싼다.
         * 파라미터: RepoRawData data -> 수집된 레포 원시 데이터
         * 반환값: RepoFetchOutcome -> 정상 수집 결과
         */
        static RepoFetchOutcome of(RepoRawData data) {
            return new RepoFetchOutcome(data, false);
        }
    }

    /**
     * 함수 이름: analyze()
     * 기능: username 계정의 공개 레포를 커밋 컷 없이 전체 기준으로 분석한다. analyze(username, null)에 위임한다.
     * 파라미터: String username -> 분석할 GitHub 계정명
     * 반환값: GithubAnalysisRawResult -> 레포별 원시 수집 결과와 레이트리밋 여부
     * 예외: GithubUserNotFoundException -> GET /users/{u}가 404
     *       GithubOrganizationAccountException -> 대상이 조직(Organization) 계정
     */
    public GithubAnalysisRawResult analyze(String username) {
        return analyze(username, null);
    }

    /**
     * 함수 이름: analyze()
     * 기능: E11-5 — 합격자 제보 시점 컷 버전. {@code commitCutoffInclusive}가 주어지면 그 날짜(포함)
     * 이후 커밋은 집계하지 않는다("합격 당시 상태 복원"). null이면 기존 사용자 본인 동의 플로우와
     * 동일하게 컷 없이 전체 커밋을 본다.
     *
     * 컷을 적용하면 레포 단위 커밋 조회에 {@code until} 쿼리 파라미터가 붙어 그 시점 이후
     * 커밋은 애초에 응답에 담기지 않는다. 그 결과 author 커밋 수가 0인 레포(컷 시점엔
     * 아직 기여하지 않았던 레포)는 결과에서 제외한다 — fork/archived 제외와 같은 층위의 필터다.
     * 단, 파일 트리·의존성 조회는 현재 default 브랜치 기준이라 컷을 반영하지 않는다(레포 존재
     * 자체가 컷 이전 기여로 확인된 것만 남기는 근사치 — 과거 스냅샷 트리 조회는 비용이 커 범위 밖).
     * 파라미터: String username -> 분석할 GitHub 계정명
     *           LocalDate commitCutoffInclusive -> 커밋 집계 상한 날짜(포함), null이면 컷 없음
     * 반환값: GithubAnalysisRawResult -> 레포별 원시 수집 결과와 레이트리밋 여부(걸리면 부분 결과)
     * 예외: GithubUserNotFoundException -> GET /users/{u}가 404
     *       GithubOrganizationAccountException -> 대상이 조직(Organization) 계정
     */
    public GithubAnalysisRawResult analyze(String username, LocalDate commitCutoffInclusive) {
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
            RepoFetchOutcome outcome = fetchRepoData(client, anonymousClient, repoOwnerLogin, username, repo, commitCutoffInclusive);
            if (outcome.rateLimited()) {
                rateLimited = true;
                break;
            }
            if (commitCutoffInclusive != null && outcome.data().commitCount() == 0) {
                // 컷 이전엔 이 레포에 author 커밋이 없었다 — 합격 당시 상태엔 존재하지 않던 기여.
                continue;
            }
            results.add(outcome.data());
        }
        return new GithubAnalysisRawResult(results, rateLimited);
    }

    /**
     * 함수 이름: discoverContributedRepos()
     * 기능: GET /search/commits?q=author:{u}로 이 사람이 커밋한 레포(조직 소유 포함)를 찾는다.
     * 비공개 조직 멤버십이면 /users/{u}/orgs는 빈 배열을 돌려주지만, 커밋 검색은 author 필터로
     * 실제 기여 레포를 찾아낸다(공개 레포에 한함 — 검색 API 자체가 비공개 레포는 접근 권한
     * 없이는 찾지 못한다). 검색 1회만 호출하고, 실패(403/429 등 별도 쿼터 소진 포함)해도
     * 예외를 던지지 않고 빈 목록을 돌려준다 — 소유 레포만으로 분석을 계속 진행하기 위함이다.
     * 파라미터: WebClient client -> 인증(토큰 설정 시) 클라이언트
     *           WebClient anonymousClient -> 403 폴백용 익명 클라이언트
     *           String username -> 커밋 작성자 GitHub 계정명
     *           List<Map<String, Object>> ownedRepos -> 이미 확보한 소유 레포 목록(중복 제외용)
     *           int maxCandidates -> 메타데이터를 조회할 후보 레포 상한
     * 반환값: List<Map<String, Object>> -> 새로 발견한 비소유 레포의 메타데이터 목록(실패 시 빈 목록)
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

    /**
     * 함수 이름: fetchRepoMeta()
     * 기능: 검색으로 찾은 레포의 archived·fork·default_branch·pushed_at 등 전체 메타데이터를 확인한다.
     * 실패하면 로그만 남기고 null을 돌려준다.
     * 파라미터: WebClient client -> 인증 클라이언트
     *           WebClient anonymousClient -> 403 폴백용 익명 클라이언트
     *           String ownerLogin -> 레포 소유자 로그인
     *           String repoName -> 레포 이름
     * 반환값: Map<String, Object> -> 레포 메타데이터(조회 실패 시 null)
     */
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

    /**
     * 함수 이름: ownerLoginOf()
     * 기능: 레포 메타데이터에서 소유자 로그인을 꺼낸다. repos 목록·검색 메타데이터 모두 "owner.login" 필드를 갖는다.
     * 없으면 분석 대상 계정으로 가정.
     * 파라미터: Map<String, Object> repo -> 레포 메타데이터
     *           String fallbackUsername -> owner.login이 없을 때 쓸 분석 대상 계정명
     * 반환값: String -> 소유자 로그인
     */
    private static String ownerLoginOf(Map<String, Object> repo, String fallbackUsername) {
        Object owner = repo.get("owner");
        if (owner instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
            return String.valueOf(ownerMap.get("login"));
        }
        return fallbackUsername;
    }

    /**
     * 함수 이름: fetchRepoData()
     * 기능: 레포 하나의 languages·커밋(샘플·정확한 총수·첫/마지막 날짜)·파일 트리·루트 의존성 파일을
     * 차례로 조회해 RepoRawData로 모은다. 컷 날짜가 있으면 커밋 조회에 until을 붙인다.
     * 레이트리밋이 감지되면 즉시 blocked 결과를 돌려주고, 그 외 개별 조회 실패는 로그만 남기고 건너뛴다.
     * 파라미터: WebClient client -> 인증 클라이언트
     *           WebClient anonymousClient -> 403 폴백용 익명 클라이언트
     *           String repoOwnerLogin -> 레포 소유자 로그인
     *           String authorUsername -> 커밋 작성자 필터에 쓸 분석 대상 계정명
     *           Map<String, Object> repoMeta -> 레포 메타데이터(name·description·default_branch 등)
     *           LocalDate commitCutoffInclusive -> 커밋 집계 상한 날짜(포함), null이면 컷 없음
     * 반환값: RepoFetchOutcome -> 수집 결과 또는 레이트리밋 차단 표시
     */
    private RepoFetchOutcome fetchRepoData(
            WebClient client, WebClient anonymousClient, String repoOwnerLogin, String authorUsername,
            Map<String, Object> repoMeta, LocalDate commitCutoffInclusive) {
        // GitHub의 until은 "이 시각 이전"(배타)이므로 컷 날짜를 포함하려면 다음날 00:00 UTC를 넘긴다.
        String untilParam = commitCutoffInclusive != null
                ? commitCutoffInclusive.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString()
                : null;
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

        int sampleCommitCount = 0;
        LocalDate first = null;
        LocalDate last = null;
        try {
            ResponseEntity<List> resp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri(uriBuilder -> {
                                uriBuilder.path("/repos/{owner}/{repo}/commits")
                                        .queryParam("author", authorUsername)
                                        .queryParam("per_page", commitsPerRepo);
                                if (untilParam != null) {
                                    uriBuilder.queryParam("until", untilParam);
                                }
                                return uriBuilder.build(repoOwnerLogin, repoName);
                            })
                            .retrieve()
                            .toEntity(List.class)
                            .block(CALL_TIMEOUT));
            if (resp != null && isRateLimited(resp.getHeaders(), resp.getStatusCode())) {
                return RepoFetchOutcome.blocked();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commits = resp != null ? (List<Map<String, Object>>) (List<?>) resp.getBody() : null;
            if (commits != null) {
                sampleCommitCount = commits.size();
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

        // 정확한 author 커밋 총수 — GitHub Link 헤더 페이지네이션 트릭. per_page=1로 요청하면
        // 응답 Link 헤더의 rel="last"에 담긴 page=N이 곧 전체 페이지 수(=페이지당 1건이므로
        // 총 커밋 수)다. 위 sampleCommitCount는 per_page=50 한 페이지뿐이라 50건에서 포화되므로
        // (198커밋 레포도 "50"으로 보임) 커밋 가중치·상위 5개 레포 선정이 왜곡됐다 — 레포당
        // 요청 비용 +1로 이 값을 정확히 구한다. Link 헤더가 없으면 커밋이 0~1개라는 뜻이라
        // 응답 배열 크기를 그대로 쓴다. 이 호출이 실패하면(레이트리밋 제외) 분석 전체를
        // 실패시키지 않고 위에서 구한 샘플 개수로 조용히 폴백한다.
        Integer exactCommitCount = null;
        try {
            ResponseEntity<List> countResp = requestWithAnonymousFallback(client, anonymousClient, fullName,
                    c -> c.get()
                            .uri(uriBuilder -> {
                                uriBuilder.path("/repos/{owner}/{repo}/commits")
                                        .queryParam("author", authorUsername)
                                        .queryParam("per_page", 1);
                                if (untilParam != null) {
                                    uriBuilder.queryParam("until", untilParam);
                                }
                                return uriBuilder.build(repoOwnerLogin, repoName);
                            })
                            .retrieve()
                            .toEntity(List.class)
                            .block(CALL_TIMEOUT));
            if (countResp != null && isRateLimited(countResp.getHeaders(), countResp.getStatusCode())) {
                return RepoFetchOutcome.blocked();
            }
            if (countResp != null) {
                Integer lastPage = parseLastPageFromLinkHeader(countResp.getHeaders().getFirst(HttpHeaders.LINK));
                if (lastPage != null) {
                    exactCommitCount = lastPage;
                } else {
                    List<?> body = countResp.getBody();
                    exactCommitCount = body != null ? body.size() : 0;
                }
            }
        } catch (WebClientResponseException e) {
            if (isRateLimitStatus(e)) {
                return RepoFetchOutcome.blocked();
            }
            if (e.getStatusCode().value() != 409) {
                logSkip("GitHub 정확한 커밋 총수 조회 (샘플 개수로 폴백)", fullName, e);
            }
        }
        int commitCount = exactCommitCount != null ? exactCommitCount : sampleCommitCount;

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

    /**
     * 함수 이름: isRateLimitStatus()
     * 기능: 예외 응답이 레이트리밋에 해당하는지 판정한다. 403 + Retry-After 헤더이거나 isRateLimited 기준에 걸리면 true.
     * 파라미터: WebClientResponseException e -> GitHub API가 돌려준 오류 응답 예외
     * 반환값: boolean -> 레이트리밋이면 true
     */
    private boolean isRateLimitStatus(WebClientResponseException e) {
        HttpHeaders headers = e.getHeaders();
        if (e.getStatusCode().value() == 403 && headers.getFirst("Retry-After") != null) {
            return true;
        }
        return isRateLimited(headers, e.getStatusCode());
    }

    /**
     * 함수 이름: isRateLimited()
     * 기능: 응답 헤더·상태로 레이트리밋 여부를 판정한다. 403 + Retry-After이거나
     * X-RateLimit-Remaining이 RATE_LIMIT_STOP_THRESHOLD 미만이면 true. 헤더가 없거나 숫자가 아니면 false.
     * 파라미터: HttpHeaders headers -> 응답 헤더
     *           HttpStatusCode status -> 응답 상태 코드(null 허용)
     * 반환값: boolean -> 레이트리밋이면 true
     */
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

    /**
     * 함수 이름: hasToken()
     * 기능: GitHub API 토큰이 설정되어 있는지(null·공백이 아닌지) 확인한다.
     * 파라미터: 없음
     * 반환값: boolean -> 토큰이 있으면 true
     */
    private boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /** 인증 클라이언트와, 조직의 fine-grained PAT 차단 시 폴백용 익명 클라이언트를 함께 만든다. */
    private record Clients(WebClient authClient, WebClient anonymousClient) {}

    /**
     * 함수 이름: buildClients()
     * 기능: 큰 응답 버퍼·GitHub 공통 헤더를 적용한 익명 WebClient와, 토큰이 있으면 Authorization을 추가한
     * 인증 WebClient를 만든다. 토큰이 없으면 인증 클라이언트는 익명 클라이언트와 같은 객체다.
     * 파라미터: 없음
     * 반환값: Clients -> 인증 클라이언트와 익명 클라이언트 묶음
     */
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
     * 함수 이름: requestWithAnonymousFallback()
     * 기능: 레포 단위 GET(메타·languages·commits·트리·contents) 공통 헬퍼. 토큰이 설정된 상태에서
     * 403을 받으면 같은 요청을 Authorization 헤더 없는 익명 클라이언트로 1회만 재시도한다 —
     * fine-grained PAT를 차단하는 조직의 공개 레포는 익명 요청이 오히려 성공하기 때문이다.
     * 익명 재시도도 실패하면 그 예외를 그대로 던져 호출부의 기존 실패 처리(레이트리밋 판정/
     * 건너뜀)를 그대로 태운다.
     * 파라미터: WebClient authClient -> 인증 클라이언트
     *           WebClient anonymousClient -> 403 폴백용 익명 클라이언트
     *           String targetFullName -> 로그에 남길 대상 레포 전체 이름(owner/repo)
     *           Function<WebClient, ResponseEntity<T>> request -> 클라이언트를 받아 요청을 수행하는 함수
     * 반환값: ResponseEntity<T> -> 요청 응답(인증 실패 시 익명 재시도 응답)
     * 예외: WebClientResponseException -> 403 외 오류이거나 익명 재시도도 실패한 경우
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
     * 함수 이름: logSkip()
     * 기능: 실패를 조용히 건너뛰는 지점의 로그를 운영(INFO)에서도 보이도록 warn으로 남긴다. 상태코드·대상만
     * 남기고 토큰 등 민감정보는 남기지 않는다.
     *
     * ⚠️ 예전엔 WebClientResponseException이 아닌 예외(DataBufferLimitException 등)도
     * "status=200"처럼 실제 HTTP 응답 상태만 보이는 형태로 찍혀, 마치 정상 응답을 받고도 실패한
     * 것처럼 오독됐다(운영에서 실측: "GitHub 커밋 검색 실패: author:... - status=200"). 예외
     * 클래스명을 항상 함께 남기고, status는 실제로 HTTP 응답에서 온 WebClientResponseException일
     * 때만 붙인다.
     * 파라미터: String action -> 건너뛴 작업 설명
     *           String targetFullName -> 대상(레포 전체 이름 또는 검색 조건)
     *           Exception e -> 발생한 예외
     * 반환값: 없음
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

    /**
     * 함수 이름: extractCommitDate()
     * 기능: 커밋 JSON에서 author 날짜(없으면 committer 날짜)를 읽어 LocalDate로 변환한다. 없거나 파싱 실패 시 null.
     * 파라미터: Map<String, Object> commit -> GitHub 커밋 응답 한 건
     * 반환값: LocalDate -> 커밋 날짜(추출 불가 시 null)
     */
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

    /**
     * 함수 이름: extractDateField()
     * 기능: author 또는 committer 객체에서 "date" 문자열을 꺼낸다.
     * 파라미터: Object authorOrCommitter -> 커밋의 author 또는 committer 값(Map이 아니면 무시)
     * 반환값: String -> 날짜 문자열(없으면 null)
     */
    private String extractDateField(Object authorOrCommitter) {
        if (authorOrCommitter instanceof Map<?, ?> m && m.get("date") != null) {
            return String.valueOf(m.get("date"));
        }
        return null;
    }

    private static final java.util.regex.Pattern LINK_LAST_PAGE_PATTERN =
            java.util.regex.Pattern.compile("<[^>]*[?&]page=(\\d+)[^>]*>\\s*;\\s*rel=\"last\"");

    /**
     * 함수 이름: parseLastPageFromLinkHeader()
     * 기능: Link 헤더 예시: {@code <...?page=2>; rel="next", <...?page=198>; rel="last"}.
     * rel="last" 항목의 URL에서 page 쿼리 파라미터 값을 뽑는다 — per_page=1 요청이므로 이 값이
     * 곧 전체 커밋 수다. 매칭되는 rel="last"가 없으면(커밋 0~1개) null을 돌려줘 호출부가
     * 응답 배열 크기로 대체하게 한다.
     * 파라미터: String linkHeader -> 응답의 Link 헤더 값(null·공백 허용)
     * 반환값: Integer -> 마지막 페이지 번호(없으면 null)
     */
    private static Integer parseLastPageFromLinkHeader(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        var matcher = LINK_LAST_PAGE_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 함수 이름: decodeBase64()
     * 기능: 공백·개행을 제거한 뒤 Base64 문자열을 UTF-8 텍스트로 디코딩한다(GitHub contents API 응답용).
     * 파라미터: String content -> Base64로 인코딩된 문자열
     * 반환값: String -> 디코딩된 텍스트
     * 예외: IllegalArgumentException -> 올바른 Base64가 아닌 경우
     */
    private static String decodeBase64(String content) {
        String cleaned = content.replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(cleaned);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 함수 이름: extractDependencies()
     * 기능: 파일 종류별로 의존성 "이름"만 뽑아낸다 — 버전·정확한 파서 트리는 필요 없다(키워드 매칭용).
     * 지원하지 않는 파일이거나 파싱에 실패하면 빈 목록을 돌려준다.
     * 파라미터: String fileName -> 의존성 파일명(package.json, pom.xml 등)
     *           String content -> 파일 내용
     * 반환값: List<String> -> 의존성 이름 목록
     */
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

    /**
     * 함수 이름: extractFromPackageJson()
     * 기능: package.json의 dependencies·devDependencies 키(패키지명)를 모은다.
     * 파라미터: String content -> package.json 내용
     * 반환값: List<String> -> 패키지명 목록
     * 예외: Exception -> JSON 파싱 실패 시(호출부 extractDependencies가 처리)
     */
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

    /**
     * 함수 이름: extractFromPomXml()
     * 기능: pom.xml에서 정규식으로 모든 artifactId 값을 모은다.
     * 파라미터: String content -> pom.xml 내용
     * 반환값: List<String> -> artifactId 목록
     */
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

    /**
     * 함수 이름: extractFromGradle()
     * 기능: build.gradle(.kts)의 implementation·api 등 의존성 선언에서 artifact 이름(group:artifact:version의 artifact)을 모은다.
     * 파라미터: String content -> gradle 빌드 스크립트 내용
     * 반환값: List<String> -> 의존성 artifact 이름 목록
     */
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

    /**
     * 함수 이름: extractFromRequirementsTxt()
     * 기능: requirements.txt를 줄 단위로 읽어 주석·옵션 줄을 제외하고 버전 지정자 앞의 패키지명만 모은다.
     * 파라미터: String content -> requirements.txt 내용
     * 반환값: List<String> -> 패키지명 목록
     */
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

    /**
     * 함수 이름: extractFromPyprojectToml()
     * 기능: pyproject.toml에서 따옴표로 감싼 패키지 형태 문자열을 정규식으로 찾아 패키지명을 모은다.
     * 파라미터: String content -> pyproject.toml 내용
     * 반환값: List<String> -> 패키지명 목록
     */
    private List<String> extractFromPyprojectToml(String content) {
        List<String> result = new ArrayList<>();
        var matcher = PYPROJECT_DEP_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * 함수 이름: computeActiveMonths()
     * 기능: 첫 커밋 월부터 마지막 커밋 월까지의 활동 개월 수(양 끝 포함, 최소 1)를 계산한다.
     * 커밋이 없거나 날짜가 null이면 0.
     * 파라미터: LocalDate first -> 첫 커밋 날짜
     *           LocalDate last -> 마지막 커밋 날짜
     *           int commitCount -> 커밋 수
     * 반환값: int -> 활동 개월 수
     */
    static int computeActiveMonths(LocalDate first, LocalDate last, int commitCount) {
        if (commitCount <= 0 || first == null || last == null) return 0;
        long months = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), last.withDayOfMonth(1));
        return (int) Math.max(1, months + 1);
    }
}
