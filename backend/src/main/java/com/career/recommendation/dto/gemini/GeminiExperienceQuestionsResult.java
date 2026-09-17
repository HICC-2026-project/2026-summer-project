package com.career.recommendation.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * E11-6 — Gemini 경험 심층 질문 응답 JSON을 타입 안전하게 파싱하기 위한 DTO.
 *
 * 예시 응답: {"questions": ["토큰 만료는 어떻게 처리했나요?", "..."]}
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiExperienceQuestionsResult {

    private List<String> questions;
}
