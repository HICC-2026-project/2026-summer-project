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
     * 활동 추천 생성 (DB 활동 기반 RAG 패턴)
     * @param userSpecJson          사용자 스펙 (JSON 문자열)
     * @param targetJob             목표 직무
     * @param similarCases          유사 합격자 케이스 요약 (SimilarSpecFinder에서 생성)
     * @param availableActivitiesJson DB에 등록된 활성 활동 목록 (JSON 배열 문자열)
     * @return Gemini API 응답 JSON 텍스트 (파싱 실패 시 빈 문자열)
     */
    public String generateRecommendation(String userSpecJson, String targetJob,
                                         String similarCases, String availableActivitiesJson) {
        String prompt = buildRecommendationPrompt(userSpecJson, targetJob, similarCases, availableActivitiesJson);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 스펙을 분석하고, 제공된 활동 목록 중에서만 맞춤형 활동을 추천해 주세요. 목록에 없는 활동을 임의로 만들지 마세요. 반드시 JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    /**
     * 커리어 로드맵 생성 (DB 활동 기반 RAG 패턴 + F-03 연계 및 유사 합격자 맥락 반영)
     * @param userSpecJson               사용자 스펙 JSON
     * @param targetJob                  목표 직무
     * @param grade                      현재 학년 (null이면 학기 구분 없이 월별 단위)
     * @param similarCases               유사 합격자 케이스 요약 (SimilarSpecFinder)
     * @param topRecommendedActivities  F-03에서 우선 추천된 활동 목록 요약 (JSON 문자열)
     * @param availableActivitiesJson     DB에 등록된 활성 활동 목록 (JSON 배열 문자열)
     */
    public String generateRoadmap(String userSpecJson, String targetJob, Integer grade,
                                  String similarCases, String topRecommendedActivities,
                                  String availableActivitiesJson) {
        String prompt = buildRoadmapPrompt(userSpecJson, targetJob, grade, similarCases, topRecommendedActivities, availableActivitiesJson);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 현재 스펙과 목표 직무, 유사 합격자 데이터 및 우선 추천 활동을 기반으로 시기별 커리어 로드맵을 생성하되, 단기 기간에는 제공된 DB 활동 목록 중 최적의 활동을 매칭하고, 먼 미래 기간에 적합한 공고가 없으면 역량 준비 가이드를 제안하세요. JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    private String buildRecommendationPrompt(String spec, String job, String cases, String availableActivities) {
        return String.format("""
                [사용자 현재 스펙]
                %s
                
                [목표 직무]
                %s
                
                [유사 합격자 케이스]
                %s
                
                [추천 가능한 활동 목록 (DB 등록 활동)]
                %s
                
                ## 규칙
                1. 반드시 위 "추천 가능한 활동 목록"에 있는 활동 중에서만 선택하세요.
                2. 각 활동의 id 값을 그대로 사용하세요 (UUID 형식). 목록에 없는 ID는 절대로 임의로 생성하지 마세요.
                3. 사용자의 스펙과 목표 직무에 가장 적합한 활동을 최대 5개 골라 추천하세요.
                4. 목록에 적합한 활동이 5개 미만이면 있는 만큼만 추천하세요.
                5. 각 추천에는 id(UUID), name, type, reason(이 사용자에게 추천하는 구체적 이유), deadline(YYYY-MM-DD) 필드를 포함하세요.
                6. 응답은 {"activities": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, cases, availableActivities);
    }

    private String buildRoadmapPrompt(String spec, String job, Integer grade,
                                      String cases, String topRecommended, String availableActivities) {
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
                
                [유사 합격자 케이스]
                %s
                
                %s
                
                [우선 반영할 AI 추천 활동 (F-03 결과)]
                %s
                
                [전체 DB 등록 활동 목록]
                %s
                
                ## 규칙
                1. 6개월 커리어 로드맵을 위 기간 단위로 작성해 주세요.
                2. [유사 합격자 케이스]의 준비 경험(자격증, 경험 수 등) 및 시기별 흐름을 반영하세요.
                3. [우선 반영할 AI 추천 활동]에 포함된 활동들을 6개월 타임라인 중 적절한 시기에 우선적으로 배치하세요.
                4. 단기 기간(1~3개월 차)에는 [전체 DB 등록 활동 목록]에서 마감일과 직무가 적합한 실제 활동의 ID를 매칭하세요.
                5. 장기 기간(4~6개월 차)에 적합한 DB 활동 공고가 없거나 마감된 경우, activityIds는 빈 배열([])로 두고, 해당 시기에 필수적으로 준비해야 할 역량 개발 가이드(예: "자격증 취득 및 포트폴리오 구체화", "알고리즘 코딩테스트 대비", "주요 부스트캠프/인턴십 차기 기수 모집 대비")를 activity 필드와 reason 필드에 설명하세요.
                6. DB 활동을 매칭할 때 각 활동의 id 값을 그대로 사용하세요 (UUID 형식). 목록에 없는 ID는 절대로 임의로 만들지 마세요.
                7. 각 시기에 최대 3개의 활동 또는 준비 가이드를 추천하세요.
                8. 각 항목에는 period(시기 설명), priority(HIGH/MEDIUM/LOW), activity(활동명 또는 역량 준비 가이드 텍스트), reason(이유), activityIds(UUID 배열, 적합한 DB 활동이 없는 경우 빈 배열 []) 필드를 포함하세요.
                9. 응답은 {"timeline": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, cases, periodGuide, topRecommended, availableActivities);
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
