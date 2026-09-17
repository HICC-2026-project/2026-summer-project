package com.career.recommendation.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gemini-2.5-flash는 기본적으로 thinking이 켜져 있어, 보이지 않는 thinking 토큰까지 output으로
 * 과금될 수 있다. generationConfig에 thinkingBudget:0과 maxOutputTokens가 실제 요청 바디에
 * 실리는지 MockWebServer로 검증한다(실제 응답 파싱·폴백 흐름은 GeminiServiceRequestTest가 이미 덮는다).
 */
class GeminiServiceGenerationConfigTest {

    private static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"activities\\\":[]}\"}]}}]}";

    private MockWebServer server;
    private GeminiService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        service = new GeminiService(WebClient.builder(), new GeminiDailyQuota(0), new GeminiCallStats());
        ReflectionTestUtils.setField(service, "apiKey", "secret-key");
        ReflectionTestUtils.setField(service, "keyInHeader", true);
        String baseUrl = server.url("/").toString();
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl.substring(0, baseUrl.length() - 1));
        ReflectionTestUtils.setField(service, "model", "gemini-test");
        ReflectionTestUtils.setField(service, "maxOutputTokens", 2048);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 요청_바디에_thinkingBudget_0과_maxOutputTokens가_실린다() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(OK_BODY));

        service.generateRecommendation("{}", "BACKEND", "ctx", "[]", java.time.LocalDate.of(2026, 8, 23));

        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();

        assertThat(body).contains("\"thinkingConfig\":{\"thinkingBudget\":0}");
        assertThat(body).contains("\"maxOutputTokens\":2048");
        assertThat(body).contains("\"responseMimeType\":\"application/json\"");
    }
}
