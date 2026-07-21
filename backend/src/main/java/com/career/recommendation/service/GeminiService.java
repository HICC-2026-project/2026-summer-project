package com.career.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — Gemini API 연동 서비스
 * 추천 생성 및 로드맵 생성 프롬프트를 관리하며 Google Gemini API를 호출합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    private final WebClient.Builder webClientBuilder;

    /** JSON 블록만 추출하는 패턴 (응답 앞뒤 잡담 제거) */
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}", Pattern.DOTALL);

    /**
     * 활동 추천 생성
     * @param userSpecJson  사용자 스펙 (JSON 문자열)
     * @param targetJob     목표 직무
     * @param similarCases  유사 합격자 케이스 요약 (SimilarSpecFinder에서 생성)
     * @return Gemini API 응답 JSON 텍스트 (파싱 실패 시 빈 문자열)
     */
    public String generateRecommendation(String userSpecJson, String targetJob, String similarCases) {
        String prompt = buildRecommendationPrompt(userSpecJson, targetJob, similarCases);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 스펙을 분석하고 맞춤형 활동을 추천해 주세요. 반드시 JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    /**
     * 커리어 로드맵 생성 (학기/방학 구분 포함)
     * @param userSpecJson 사용자 스펙 JSON
     * @param targetJob    목표 직무
     * @param grade        현재 학년 (null이면 학기 구분 없이 월별 단위)
     */
    public String generateRoadmap(String userSpecJson, String targetJob, Integer grade) {
        String prompt = buildRoadmapPrompt(userSpecJson, targetJob, grade);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 현재 스펙과 목표 직무를 기반으로 시기별 커리어 로드맵을 JSON 형식으로만 생성해 주세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    private String buildRecommendationPrompt(String spec, String job, String cases) {
        return String.format("""
                [사용자 현재 스펙]
                %s
                
                [목표 직무]
                %s
                
                [유사 합격자 케이스]
                %s
                
                matchScore 산정 기준 (0-100):
                - 학점 30%%: 합격자 평균 학점 대비 유저 학점 비율
                - 어학 25%%: TOEIC/OPIc 등급 비교
                - 자격증 20%%: 직무 관련 자격증 보유 여부
                - 경험 수 25%%: 인턴/대외활동/공모전 건수
                
                위 정보를 바탕으로 지금 당장 도전할 수 있는 인턴십·대외활동·공모전을 5개 추천해 주세요.
                각 추천에는 name, type, reason, matchScore(0-100), deadline(YYYY-MM-DD) 필드를 포함하세요.
                응답은 {"activities": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, cases);
    }

    private String buildRoadmapPrompt(String spec, String job, Integer grade) {
        String periodGuide = (grade != null)
                ? String.format("""
                현재 %d학년 학생입니다. 기간 구분은 반드시 "학기"와 "방학"을 기준으로 나눠주세요.
                예시: "3학년 2학기 (9~11월)", "겨울방학 (12월~2월)", "4학년 1학기 (3~6월)" 등.
                """, grade)
                : "기간은 월 단위로 나눠주세요. 예: \"7월\", \"8~9월\" 등.";

        return String.format("""
                [사용자 현재 스펙]
                %s
                
                [목표 직무]
                %s
                
                %s
                
                6개월 커리어 로드맵을 위 기간 단위로 작성해 주세요.
                각 항목에는 period(시기 설명), priority(HIGH/MEDIUM/LOW), activity(권장 활동), reason(이유) 필드를 포함하세요.
                응답은 {"timeline": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, periodGuide);
    }

    /**
     * Gemini API 원문 응답에서 순수 JSON 블록만 추출한다.
     */
    public String extractJsonBlock(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Matcher matcher = JSON_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group() : "";
    }

    private String callGeminiApi(String systemInstruction, String userMessage) {
        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .build();

        // Gemini generateContent API 규격에 맞춘 요청 Body 생성
        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", userMessage))
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        String uri = String.format("/models/%s:generateContent?key=%s", model, apiKey);

        try {
            Map<?, ?> response = client.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()) {
                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                if (candidate.get("content") instanceof Map<?, ?> content && content.get("parts") instanceof List<?> parts && !parts.isEmpty()) {
                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                    return (String) firstPart.get("text");
                }
            }
        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
        }
        return "";
    }
}
