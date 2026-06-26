package com.career.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

/**
 * BE-1 담당 — Claude API 연동 서비스
 * 추천 생성 및 로드맵 생성 프롬프트를 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.base-url}")
    private String baseUrl;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private int maxTokens;

    private final WebClient.Builder webClientBuilder;

    /**
     * 활동 추천 생성
     * @param userSpecJson 사용자 스펙 (JSON 문자열)
     * @param targetJob    목표 직무
     * @param similarCases 유사 합격자 케이스 (BE-2에서 조회)
     * @return Claude API 응답 텍스트
     */
    public String generateRecommendation(String userSpecJson, String targetJob, String similarCases) {
        String prompt = buildRecommendationPrompt(userSpecJson, targetJob, similarCases);
        return callClaudeApi(
                "당신은 취업 커리어 어드바이저입니다. 사용자의 스펙을 분석하고 맞춤형 활동을 추천해 주세요. " +
                "반드시 JSON 형식으로만 응답하세요.",
                prompt
        );
    }

    /**
     * 커리어 로드맵 생성
     */
    public String generateRoadmap(String userSpecJson, String targetJob) {
        String prompt = buildRoadmapPrompt(userSpecJson, targetJob);
        return callClaudeApi(
                "당신은 취업 커리어 어드바이저입니다. 사용자의 현재 스펙과 목표 직무를 기반으로 " +
                "시기별 커리어 로드맵을 JSON 형식으로만 생성해 주세요.",
                prompt
        );
    }

    private String buildRecommendationPrompt(String spec, String job, String cases) {
        return String.format("""
                [사용자 현재 스펙]
                %s
                
                [목표 직무]
                %s
                
                [유사 합격자 케이스]
                %s
                
                위 정보를 바탕으로 지금 당장 도전할 수 있는 인턴십·대외활동·공모전을 5개 추천해 주세요.
                각 추천에는 name, type, reason, matchScore(0-100), deadline 필드를 포함하세요.
                응답은 {"activities": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, cases);
    }

    private String buildRoadmapPrompt(String spec, String job) {
        return String.format("""
                [사용자 현재 스펙]
                %s
                
                [목표 직무]
                %s
                
                6개월 커리어 로드맵을 월별로 작성해 주세요.
                각 항목에는 period(YYYY-MM), priority(HIGH/MEDIUM/LOW), activity, reason 필드를 포함하세요.
                응답은 {"timeline": [...]} JSON 형식으로만 출력하세요.
                """, spec, job);
    }

    private String callClaudeApi(String systemPrompt, String userMessage) {
        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        try {
            Map<?, ?> response = client.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.get("content") instanceof List<?> contents && !contents.isEmpty()) {
                Map<?, ?> firstContent = (Map<?, ?>) contents.get(0);
                return (String) firstContent.get("text");
            }
        } catch (Exception e) {
            log.error("Claude API 호출 실패: {}", e.getMessage());
        }
        return "{}";
    }
}
