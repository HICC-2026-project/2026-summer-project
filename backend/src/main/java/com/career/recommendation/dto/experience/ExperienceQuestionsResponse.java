package com.career.recommendation.dto.experience;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * POST /api/v1/users/me/experiences/questions 응답.
 * Gemini 실패·전역 일일 상한 초과 시에도 200과 함께 questions: []를 준다(FE가 질문 단계를
 * 건너뛴다) — 이 엔드포인트 자체가 실패해도 사용자의 경험 저장 흐름을 막으면 안 된다.
 */
@Getter
@Builder
public class ExperienceQuestionsResponse {

    private List<String> questions;

    /**
     * 함수 이름: empty()
     * 기능: Gemini 실패·일일 상한 초과 시 돌려줄 응답을 만든다. questions가 빈 리스트라 FE가 질문 단계를 건너뛴다.
     * 파라미터: 없음
     * 반환값: ExperienceQuestionsResponse -> 질문이 없는 응답
     */
    public static ExperienceQuestionsResponse empty() {
        return ExperienceQuestionsResponse.builder().questions(List.of()).build();
    }
}
