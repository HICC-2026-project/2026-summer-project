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

        // repo-a: languages/commits(샘플)/commits(정확한 총수)/tree 전부 정상, 남은 한도 넉넉함
        server.enqueue(jsonResponse(Map.of("Java", 1000)).setHeader("X-RateLimit-Remaining", "50"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "50"));
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

        // owned-repo 한 건만 후속 호출(languages/commits 샘플/commits 정확 총수/tree)이 나가야 한다.
        server.enqueue(jsonResponse(Map.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()).setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())).setHeader("X-RateLimit-Remaining", "100"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("owned-repo");
        // 유저(1) + 레포목록(1) + 검색(1, 빈 결과) + owned-repo 후속 4건 = 7. fork·archive 레포는
        // 추가 호출을 만들지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(7);
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
        // 유저(1)+레포목록(1)+검색(1, 빈 결과)+languages(1)+commits 샘플(1)+commits 정확 총수(1)+
        // tree(1)+contents(1, package.json만) = 8. 루트가 아닌 pom.xml은 조회하지 않는다.
        assertThat(server.getRequestCount()).isEqualTo(8);
    }

    // --- 정확한 커밋 총수 계산(Link 헤더 페이지네이션 트릭) ---

    @Test
    void Link_헤더의_rel_last_page를_정확한_커밋_총수로_사용한다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        // 샘플 조회(per_page=50)는 50건 가득 채워 응답 — 실제로는 198건이라 여기서 포화된다.
        server.enqueue(jsonResponse(java.util.Collections.nCopies(50, Map.of())));
        // 정확한 총수 조회(per_page=1)는 Link 헤더로 총 198페이지(=198커밋)를 알려준다.
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Link",
                        "<https://api.example.com/repos/octocat/owned-repo/commits?author=octocat&per_page=1&page=2>; rel=\"next\", "
                                + "<https://api.example.com/repos/octocat/owned-repo/commits?author=octocat&per_page=1&page=198>; rel=\"last\"")
                .setBody("[{}]"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).commitCount()).isEqualTo(198);
    }

    @Test
    void Link_헤더가_없으면_응답_배열_크기를_총_커밋_수로_사용한다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        server.enqueue(jsonResponse(List.of())); // 샘플: 커밋 없음(실제로는 1건)
        server.enqueue(jsonResponse(List.of(Map.of()))); // 정확한 총수 조회: Link 헤더 없이 1건
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).commitCount()).isEqualTo(1);
    }

    @Test
    void 정확한_커밋_총수_조회가_실패하면_샘플_개수로_폴백한다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        server.enqueue(jsonResponse(List.of(Map.of(), Map.of(), Map.of()))); // 샘플 3건
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"boom\"}"));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).commitCount()).isEqualTo(3);
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
        assertThat(takeRequest().getPath()) // 정확한 커밋 총수 조회(per_page=1)
                .startsWith("/repos/hicc-org/2026-summer-project/commits")
                .contains("per_page=1");
        assertThat(takeRequest().getPath()).startsWith("/repos/hicc-org/2026-summer-project/git/trees/main");
    }

    /**
     * 운영 로그 실측: "GitHub 커밋 검색 실패: author:... - status=200" — HTTP 200인데 파싱 중
     * 예외(DataBufferLimitException). /search/commits 응답이 WebClient 기본 maxInMemorySize
     * (256KB)를 넘으면 discoverContributedRepos의 catch(Exception)가 조용히 삼켜, 실제로 존재하는
     * 기여 레포를 발견하지 못했다. buildClients()가 10MB로 올린 뒤에는 300KB급 응답도 정상
     * 파싱되어 발견에 성공해야 한다.
     */
    @Test
    void 대형_커밋_검색_응답도_기본_버퍼_크기를_넘어_파싱과_발견에_성공한다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of())); // 소유 레포 0개

        // 실제 매칭되는 레포 1건 + GitHub 검색 응답 특유의 부가 필드를 흉내 낸 대량 패딩으로
        // 응답 전체를 300KB 이상으로 부풀린다(기본 256KB 버퍼를 확실히 넘기기 위함).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total_count", 1);
        body.put("items", List.of(
                Map.of("repository", Map.of("full_name", "hicc-org/2026-summer-project", "fork", false))));
        body.put("_padding_to_exceed_default_buffer", "x".repeat(300_000));
        server.enqueue(jsonResponse(body));

        server.enqueue(jsonResponse(orgRepo("2026-summer-project", "hicc-org", false, false)));
        server.enqueue(jsonResponse(Map.of("Java", 500)));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos())
                .as("기본 버퍼 크기(256KB)를 넘는 검색 응답도 파싱되어 기여 레포가 발견돼야 한다")
                .extracting(GithubClient.RepoRawData::name)
                .containsExactly("2026-summer-project");
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
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("real-thing");
        // fork 후보는 메타데이터 조회 자체를 만들지 않는다: 유저(1)+소유(1)+검색(1)+
        // real-thing 메타(1)+languages(1)+commits 샘플(1)+commits 정확 총수(1)+tree(1) = 8.
        assertThat(server.getRequestCount()).isEqualTo(8);
    }

    @Test
    void 검색_API가_403이어도_소유_레포만으로_정상_완료된다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"rate limit\"}"));
        server.enqueue(jsonResponse(Map.of()));
        server.enqueue(jsonResponse(List.of()));
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
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("dup-repo");
        // 이미 소유 목록에 있으므로 메타데이터 재조회 없음: 유저(1)+소유(1)+검색(1)+
        // dup-repo 후속 4건(languages/commits 샘플/commits 정확 총수/tree) = 7.
        assertThat(server.getRequestCount()).isEqualTo(7);
    }

    // --- 조직의 fine-grained PAT 차단 시 익명 폴백 ---

    @Test
    void 메타_조회가_403이면_익명으로_재시도하고_성공하면_레포가_분석에_포함된다() throws InterruptedException {
        ReflectionTestUtils.setField(client, "token", "test-token");

        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of())); // 소유 레포 0개
        server.enqueue(searchResponse(Map.of("full_name", "hicc-org/2026-summer-project", "fork", false)));
        server.enqueue(new MockResponse().setResponseCode(403).setBody(
                "{\"message\":\"The 'hicc-org' organization forbids access via a fine-grained personal access token.\"}"));
        server.enqueue(jsonResponse(orgRepo("2026-summer-project", "hicc-org", false, false)));
        server.enqueue(jsonResponse(Map.of("Java", 500)));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("2026-summer-project");

        takeRequest(); // user
        takeRequest(); // owned repos
        takeRequest(); // search
        RecordedRequest metaFirst = takeRequest(); // meta - 인증 요청, 403
        assertThat(metaFirst.getHeader("Authorization")).isEqualTo("Bearer test-token");
        RecordedRequest metaRetry = takeRequest(); // meta - 익명 재시도, 200
        assertThat(metaRetry.getHeader("Authorization")).isNull();
        assertThat(metaRetry.getPath()).startsWith("/repos/hicc-org/2026-summer-project");

        assertThat(server.getRequestCount()).isEqualTo(9);
    }

    @Test
    void 메타_조회_403_후_익명_재시도도_403이면_해당_레포만_건너뛰고_나머지는_정상_수집된다() {
        ReflectionTestUtils.setField(client, "token", "test-token");

        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(searchResponse(Map.of("full_name", "blocked-org/blocked-repo", "fork", false)));
        server.enqueue(new MockResponse().setResponseCode(403).setBody(
                "{\"message\":\"The 'blocked-org' organization forbids access via a fine-grained personal access token.\"}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"Not Found\"}"));
        // owned-repo는 인증 요청으로 정상 처리된다.
        server.enqueue(jsonResponse(Map.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).extracting(GithubClient.RepoRawData::name).containsExactly("owned-repo");
        // 유저(1)+소유(1)+검색(1)+meta 인증(1)+meta 익명(1)+owned-repo 후속 4건
        // (languages/commits 샘플/commits 정확 총수/tree) = 9.
        assertThat(server.getRequestCount()).isEqualTo(9);
    }

    @Test
    void 토큰이_없으면_메타_조회_403에서_익명_재시도를_하지_않고_1회만_호출한다() {
        // setUp()에서 token은 이미 ""로 설정됨.
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(searchResponse(Map.of("full_name", "org/repo", "fork", false)));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"Not Found\"}"));

        GithubClient.GithubAnalysisRawResult result = client.analyze("octocat");

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).isEmpty();
        // 유저(1)+소유(1)+검색(1)+meta(1, 재시도 없음) = 4.
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    // --- E11-5: 합격 연도 컷오프 ---

    @Test
    void 컷오프가_있으면_커밋_조회에_until_파라미터가_붙는다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        server.enqueue(jsonResponse(List.of(Map.of())));
        server.enqueue(jsonResponse(List.of(Map.of())));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result =
                client.analyze("octocat", java.time.LocalDate.of(2026, 12, 31));

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).hasSize(1);

        takeRequest(); // user
        takeRequest(); // owned repos
        takeRequest(); // search
        takeRequest(); // languages
        // 컷 날짜(2026-12-31, 포함)의 다음날 00:00 UTC를 배타적 until로 보낸다(콜론 인코딩
        // 여부와 무관하게 연-월-일 접두사만 확인해 인코딩 구현에 덜 취약하게 한다).
        assertThat(takeRequest().getPath()).contains("until=2027-01-01");
        assertThat(takeRequest().getPath()).contains("until=2027-01-01");
    }

    @Test
    void 컷오프_적용시_컷_이전_커밋이_0건인_레포는_결과에서_제외된다() {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("too-new-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        server.enqueue(jsonResponse(List.of())); // 샘플: 컷 이전 커밋 0건
        server.enqueue(jsonResponse(List.of())); // 정확 총수: 0건
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        GithubClient.GithubAnalysisRawResult result =
                client.analyze("octocat", java.time.LocalDate.of(2020, 12, 31));

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.repos()).isEmpty();
    }

    @Test
    void 컷오프가_없으면_기존_사용자_플로우와_동일하게_until_파라미터가_없다() throws InterruptedException {
        server.enqueue(jsonResponse(Map.of("login", "octocat", "type", "User"))
                .setHeader("X-RateLimit-Remaining", "100"));
        server.enqueue(jsonResponse(List.of(repo("owned-repo", false, false))));
        server.enqueue(emptySearchResponse());

        server.enqueue(jsonResponse(Map.of("Java", 1000)));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(List.of()));
        server.enqueue(jsonResponse(Map.of("tree", List.of())));

        client.analyze("octocat");

        takeRequest(); // user
        takeRequest(); // owned repos
        takeRequest(); // search
        takeRequest(); // languages
        assertThat(takeRequest().getPath()).doesNotContain("until=");
        assertThat(takeRequest().getPath()).doesNotContain("until=");
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
