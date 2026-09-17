package com.career.recommendation.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Gemini 없이 요청 모양만 본다 — 키가 어디에 실리는지(헤더 vs 쿼리), 응답 파싱, 통계·상한 연동.
 * WebClient의 exchangeFunction을 갈아끼워 네트워크를 막는다.
 */
class GeminiServiceRequestTest {

    private static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"activities\\\":[]}\"}]}}]}";

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();
    private final GeminiCallStats stats = new GeminiCallStats();

    private GeminiService service(boolean keyInHeader, GeminiDailyQuota quota, String body) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build());
        });
        GeminiService s = new GeminiService(builder, quota, stats);
        ReflectionTestUtils.setField(s, "apiKey", "secret-key");
        ReflectionTestUtils.setField(s, "keyInHeader", keyInHeader);
        ReflectionTestUtils.setField(s, "baseUrl", "https://example.test/v1beta");
        ReflectionTestUtils.setField(s, "model", "gemini-test");
        ReflectionTestUtils.setField(s, "maxOutputTokens", 4096);
        return s;
    }

    @Test
    void 기본_설정에서는_API_키가_헤더로만_나가고_URL에는_없다() {
        GeminiService s = service(true, new GeminiDailyQuota(0), OK_BODY);

        String out = s.generateRecommendation("{}", "BACKEND", "ctx", "[]", java.time.LocalDate.of(2026, 8, 23));

        ClientRequest req = captured.get();
        assertThat(req.headers().getFirst("x-goog-api-key")).isEqualTo("secret-key");
        assertThat(req.url().toString()).doesNotContain("secret-key").doesNotContain("key=");
        assertThat(req.url().getPath()).endsWith("/models/gemini-test:generateContent");
        assertThat(out).isEqualTo("{\"activities\":[]}");
        assertThat(stats.snapshot().success()).isEqualTo(1);
    }

    @Test
    void 비상_복귀_플래그를_끄면_예전처럼_쿼리스트링으로_나간다() {
        GeminiService s = service(false, new GeminiDailyQuota(0), OK_BODY);

        s.generateRecommendation("{}", "BACKEND", "ctx", "[]", java.time.LocalDate.of(2026, 8, 23));

        ClientRequest req = captured.get();
        assertThat(req.headers().getFirst("x-goog-api-key")).isNull();
        assertThat(req.url().getQuery()).isEqualTo("key=secret-key");
    }

    @Test
    void 전역_일일_상한에_닿으면_네트워크_호출_없이_빈_문자열을_돌려준다() {
        GeminiDailyQuota quota = new GeminiDailyQuota(1);
        GeminiService s = service(true, quota, OK_BODY);

        assertThat(s.generateRecommendation("{}", "B", "c", "[]", java.time.LocalDate.of(2026, 8, 23))).isNotEmpty();
        captured.set(null);
        assertThat(s.generateRecommendation("{}", "B", "c", "[]", java.time.LocalDate.of(2026, 8, 23))).isEmpty();
        assertThat(captured.get()).isNull();
    }

    @Test
    void candidates가_비어_있으면_실패로_집계하고_빈_문자열을_돌려준다() {
        GeminiService s = service(true, new GeminiDailyQuota(0), "{\"candidates\":[]}");

        assertThat(s.generateRecommendation("{}", "B", "c", "[]", java.time.LocalDate.of(2026, 8, 23))).isEmpty();
        assertThat(stats.snapshot().failure()).isEqualTo(1);
    }
}
