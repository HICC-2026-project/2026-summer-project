package com.career.recommendation.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Gemini 로드맵(F-05) 응답 JSON을 타입 안전하게 파싱하기 위한 DTO.
 *
 * 예시 응답:
 * {
 *   "timeline": [
 *     {
 *       "period": "3학년 2학기 (9~11월)",
 *       "priority": "HIGH",
 *       "activity": "자격증 취득 및 포트폴리오 구체화",
 *       "reason": "...",
 *       "activityIds": ["uuid-1", "uuid-2"]
 *     }
 *   ]
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRoadmapResult {

    private List<GeminiTimelineStep> timeline;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiTimelineStep {
        private String period;
        private String priority;
        private String activity;
        private String reason;
        /** Gemini가 선택한 DB 활동 UUID 목록 (장기 기간에는 빈 배열) */
        private List<String> activityIds;
    }
}
