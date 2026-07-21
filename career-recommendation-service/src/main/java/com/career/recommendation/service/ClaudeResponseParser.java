package com.career.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — Claude 응답 JSON 방어 로직
 *
 * 3단계 전략:
 * 1단계: 응답 텍스트 직접 파싱
 * 2단계: 정규식으로 JSON 블록 추출 후 파싱
 * 3단계: 모두 실패 시 기본 fallback 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeResponseParser {

    private final ObjectMapper objectMapper;

    // ```json ... ``` 또는 { ... } 블록 추출용 패턴
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```json\\s*(\\{.*?})\\s*```|```\\s*(\\{.*?})\\s*```|(\\{.*})", Pattern.DOTALL);

    /**
     * Claude 응답에서 JSON 노드를 안전하게 파싱합니다.
     *
     * @param rawResponse Claude API 원본 응답 문자열
     * @param rootKey     최상위 키 (예: "activities", "timeline", "comparison")
     * @param fallback    파싱 완전 실패 시 반환할 기본 JSON 문자열
     * @return 파싱된 JsonNode (항상 non-null)
     */
    public JsonNode parse(String rawResponse, String rootKey, String fallback) {
        // 1단계: 직접 파싱 시도
        Optional<JsonNode> direct = tryParse(rawResponse);
        if (direct.isPresent() && direct.get().has(rootKey)) {
            log.debug("ClaudeResponseParser: 1단계(직접 파싱) 성공");
            return direct.get();
        }

        // 2단계: 정규식으로 JSON 블록 추출 후 파싱
        Optional<JsonNode> extracted = extractAndParse(rawResponse);
        if (extracted.isPresent() && extracted.get().has(rootKey)) {
            log.debug("ClaudeResponseParser: 2단계(정규식 추출) 성공");
            return extracted.get();
        }

        // 3단계: fallback 반환
        log.warn("ClaudeResponseParser: 3단계 fallback 사용 — rootKey={}, rawResponse 앞 200자={}",
                rootKey, rawResponse != null ? rawResponse.substring(0, Math.min(200, rawResponse.length())) : "null");
        return tryParse(fallback).orElseGet(() -> objectMapper.createObjectNode());
    }

    /**
     * 문자열이 유효한 JSON인지 확인하고 특정 키를 가지는지 검증합니다.
     */
    public boolean isValidJson(String text, String requiredKey) {
        return tryParse(text)
                .map(node -> node.has(requiredKey))
                .orElse(false);
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    private Optional<JsonNode> tryParse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        try {
            String trimmed = text.trim();
            // JSON 시작 전 불필요한 텍스트 제거 (앞에 설명 문구가 붙는 경우)
            int jsonStart = trimmed.indexOf('{');
            if (jsonStart > 0) {
                trimmed = trimmed.substring(jsonStart);
            }
            return Optional.of(objectMapper.readTree(trimmed));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<JsonNode> extractAndParse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            for (int g = 1; g <= matcher.groupCount(); g++) {
                String candidate = matcher.group(g);
                if (candidate != null) {
                    Optional<JsonNode> parsed = tryParse(candidate);
                    if (parsed.isPresent()) return parsed;
                }
            }
        }
        return Optional.empty();
    }
}
