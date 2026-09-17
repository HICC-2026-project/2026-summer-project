package com.career.recommendation.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * E11-6 — Gemini 경험 보강 응답 JSON을 타입 안전하게 파싱하기 위한 DTO.
 * 값 검증(areas는 ExperienceArea 13종만, depth는 3종만, roleSummary 100자 컷)은
 * 이 DTO가 아니라 ExperienceInsightService가 담당한다 — Gemini가 스키마 밖 값을
 * 줘도 역직렬화 자체는 성공해야 방어 로직이 정상 동작한다.
 *
 * 예시 응답: {"areas": ["API", "DB"], "depth": "IMPLEMENTED", "roleSummary": "..."}
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiExperienceEnrichmentResult {

    private List<String> areas;
    private String depth;
    private String roleSummary;
}
