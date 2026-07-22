package com.career.recommendation.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Gemini 추천(F-03) 응답 JSON을 타입 안전하게 파싱하기 위한 DTO.
 *
 * 예시 응답:
 * {
 *   "activities": [
 *     { "id": "uuid", "name": "...", "type": "...", "reason": "...", "deadline": "2026-10-31" }
 *   ]
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRecommendationResult {

    private List<GeminiActivity> activities;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiActivity {
        /** DB 활동의 UUID (문자열로 수신) */
        private String id;
        private String name;
        private String type;
        private String reason;
        private String deadline;
    }
}
