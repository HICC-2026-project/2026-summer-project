package com.career.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 네트워크 없이 GitHub REST API 응답을 MockWebServer로 흉내 낸다 — 404/조직 계정/
 * 레이트리밋 중단/fork 제외/의존성 파일 파싱을 검증한다.
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

        // owned-repo 한 건만 후속 호출(languages/commits/tree)이 나가야 한다.
        server.enqueue(jsonResponse(Map.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())).setHeader("X-RateLimit-Remaining", "100"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("owned-repo");
        // 유저(1) + 레포목록(1) + owned-repo 후속 3건 = 5. fork·archive 레포는 추가 호출을 만들지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    void 루트에_있는_의존성_파일만_조회해_의존성_이름을_추출한다() throws Exception {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("backend-app", false, false)))
                .setHeader("X-RateLimit-Remaining", "100"));

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
        // 유저(1)+레포목록(1)+languages(1)+commits(1)+tree(1)+contents(1, package.json만) = 6.
        // 루트가 아닌 pom.xml은 조회하지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(6);
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
