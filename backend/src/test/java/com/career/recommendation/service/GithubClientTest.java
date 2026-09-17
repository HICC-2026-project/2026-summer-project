package com.career.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 네트워크 없이 GitHub REST API 응답을 MockWebServer로 흉내 낸다 — 404/조직 계정/
 * 레이트리밋 중단/fork 제외/의존성 파일 파싱/커밋 검색을 통한 조직 레포 발견을 검증한다.
 *
 * ⚠️ analyze()는 소유 레포 목록을 얻은 뒤 항상 GET /search/commits를 1회 호출한다(기여 레포
 * 발견) — 소유/조직 무관 시나리오를 다루는 아래 기존 테스트들도 이 호출에 대응하는 응답을
 * (보통 빈 items) 큐에 넣어야 요청 순서가 어긋나지 않는다.
 */
class GithubClientTest {

    private MockWebServer server;
    private GithubClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = new GithubClient(WebClient.builder(), new ObjectMapper());
        String baseUrl = server.url("/").toString();
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl.substring(0, baseUrl.length() - 1));
        ReflectionTestUtils.setField(client, "token", "");
        ReflectionTestUtils.setField(client, "maxReposWithToken", 30);
        ReflectionTestUtils.setField(client, "maxReposWithoutToken", 10);
        ReflectionTestUtils.setField(client, "commitsPerRepo", 50);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 존재하지_않는_계정은_404를_그대로_예외로_변환한다() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> client.analyze("no-such-user"))
                .isInstanceOf(com.career.recommendation.exception.GithubUserNotFoundException.class);
    }

    @Test
    void 조직_계정은_레포_조회_없이_바로_거부된다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "hicc-org", "type", "Organization")));

        assertThatThrownBy(() -> client.analyze("hicc-org"))
                .isInstanceOf(com.career.recommendation.exception.GithubOrganizationAccountException.class);

        // 레포 목록 API는 호출되지 않아야 한다 — 유저 조회 1건만 나갔는지 확인.
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void 레이트리밋_한도에_닿으면_그때까지_모은_부분_결과와_함께_중단한다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("repo-a", false, false), repo("repo-b", false, false)))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(emptySearchResponse());

        // repo-a: languages/commits/tree 전부 정상, 남은 한도 넉넉함
        server.enqueue(jsonResponse(Map.of("Java", 1000)).setHeader("X-RateLimit-Remaining", "50"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "50"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())).setHeader("X-RateLimit-Remaining", "50"));

        // repo-b: languages 조회 시점에 한도 3(<5) — 여기서 즉시 중단.
        server.enqueue(jsonResponse(Map.of("Java", 500)).setHeader("X-RateLimit-Remaining", "3"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isTrue();
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).name()).isEqualTo("repo-a");
    }

    @Test
    void fork와_archive_레포는_분석_대상에서_제외된다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(
                        repo("owned-repo", false, false),
                        repo("forked-repo", true, false),
                        repo("archived-repo", false, true)))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(emptySearchResponse());

        // owned-repo 한 건만 후속 호출(languages/commits/tree)이 나가야 한다.
        server.enqueue(jsonResponse(Map.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())).setHeader("X-RateLimit-Remaining", "100"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("owned-repo");
        // 유저(1) + 레포목록(1) + 검색(1, 빈 결과) + owned-repo 후속 3건 = 6. fork·archive 레포는
        // 추가 호출을 만들지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(6);
    }

    @Test
    void 루트에_있는_의존성_파일만_조회해_의존성_이름을_추출한다() throws Exception {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("backend-app", false, false)))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(Map.of("tree", List.of(
                        Map.of("path", "package.json", "type", "blob"),
                        Map.of("path", "src/nested/pom.xml", "type", "blob") // 루트가 아니므로 조회 안 함
                )))
                .setHeader("X-RateLimit-Remaining", "100"));

        String packageJson = "{\"dependencies\":{\"spring-boot-starter-web\":\"1.0\"},\"devDependencies\":{\"eslint\":\"1.0\"}}";
        String base64Content = Base64.getEncoder().encodeToString(packageJson.getBytes(StandardCharsets.UTF_8));
        server.enqueue(jsonResponse(Map.of("content", base64Content, "encoding", "base64"))
                .setHeader("X-RateLimit-Remaining", "100"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).dependencies())
                .contains("spring-boot-starter-web", "eslint");
        // 유저(1)+레포목록(1)+검색(1, 빈 결과)+languages(1)+commits(1)+tree(1)+contents(1, package.json만) = 7.
        // 루트가 아닌 pom.xml은 조회하지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(7);
    }

    // --- 커밋 검색을 통한 기여 레포 발견 ---

    @Test
    void 소유_레포가_없어도_커밋_검색이_찾은_조직_레포가_분석에_포함된다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of())); // 소유 레포 0개
        server.enqueue(searchResponse(Map.of("full_name", "hicc-org/2026-summer-project", "fork", false)));
        server.enqueue(jsonResponse(orgRepo("2026-summer-project", "hicc-org", false, false)));
        server.enqueue(jsonResponse(Map.of("Java", 500)));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("2026-summer-project");

        // languages/commits/tree 호출이 분석 대상 계정(octocat)이 아니라 실제 소유 조직
        // (hicc-org)의 경로로 나갔는지 확인한다 — 이번 보강의 핵심 회귀 지점.
        takeRequest(); // user
        takeRequest(); // owned repos
        takeRequest(); // search
        takeRequest(); // repo meta
        assertThat(takeRequest().getPath()).startsWith("/repos/hicc-org/2026-summer-project/languages");
        assertThat(takeRequest().getPath())
                .startsWith("/repos/hicc-org/2026-summer-project/commits")
                .contains("author=octocat");
        assertThat(takeRequest().getPath()).startsWith("/repos/hicc-org/2026-summer-project/git/trees/main");
    }

    @Test
    void 검색_결과의_fork_레포는_메타데이터_조회_없이_제외된다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(searchResponse(
                Map.of("full_name", "someorg/forked-thing", "fork", true),
                Map.of("full_name", "someorg/real-thing", "fork", false)
        ));
        server.enqueue(jsonResponse(orgRepo("real-thing", "someorg", false, false)));
        server.enqueue(jsonResponse(Map.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("real-thing");
        // fork 후보는 메타데이터 조회 자체를 만들지 않는다: 유저(1)+소유(1)+검색(1)+
        // real-thing 메타(1)+languages(1)+commits(1)+tree(1) = 7.
        assertThat(server.getRequestCount()).isEqualTo(7);
    }

    @Test
    void 검색_API가_403이어도_소유_레포만으로_정상_완료된다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"rate limit\"}"));
        server.enqueue(jsonResponse(Map.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("owned-repo");
    }

    @Test
    void 소유_레포와_검색_결과가_같은_레포면_한_번만_분석한다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("dup-repo", false, false)))); // full_name: octocat/dup-repo
        server.enqueue(searchResponse(Map.of("full_name", "octocat/dup-repo", "fork", false)));
        server.enqueue(jsonResponse(Map.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("dup-repo");
        // 이미 소유 목록에 있으므로 메타데이터 재조회 없음: 유저(1)+소유(1)+검색(1)+
        // dup-repo 후속 3건 = 6.
        assertThat(server.getRequestCount()).isEqualTo(6);
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        return server.takeRequest();
    }

    private static MockResponse emptySearchResponse() {
        return jsonResponse(Map.of("items", List.of()));
    }

    @SafeVarargs
    private static MockResponse searchResponse(Map<String, Object>... repositories) {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> repository : repositories) {
            items.add(Map.of("repository", repository));
        }
        return jsonResponse(Map.of("items", items));
    }

    private static Map<String, Object> orgRepo(String name, String ownerLogin, boolean fork, boolean archived) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("full_name", ownerLogin + "/" + name);
        m.put("fork", fork);
        m.put("archived", archived);
        m.put("default_branch", "main");
        m.put("pushed_at", "2024-06-01T00:00:00Z");
        m.put("description", null);
        m.put("owner", Map.of("login", ownerLogin));
        return m;
    }

    private static MockResponse jsonResponse(Object body) throws AssertionError {
        try {
            String json = new ObjectMapper().writeValueAsString(body);
            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> repo(String name, boolean fork, boolean archived) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("full_name", "octocat/" + name);
        m.put("fork", fork);
        m.put("archived", archived);
        m.put("default_branch", "main");
        m.put("pushed_at", "2024-01-01T00:00:00Z");
        m.put("description", null);
        return m;
    }
}
