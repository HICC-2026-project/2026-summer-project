package com.career.recommendation.controller;

import com.career.recommendation.exception.AiDailyLimitExceededException;
import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.ExperienceInsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler의 AiDailyLimitExceededException → 429 매핑과, ExperienceRequest의
 * 기존 검증(제목 필수 등)·새 answers 검증(1~3개, 각 1000자 이하)이 컨트롤러 계층까지
 * 정확히 이어지는지 확인한다.
 */
@WebMvcTest(ExperienceInsightController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExperienceInsightControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExperienceInsightService experienceInsightService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 경험_제목이_없으면_질문_생성이_400을_반환한다() throws Exception {
        String body = """
                {"experience": {"type": "PROJECT"}}
                """;

        mockMvc.perform(post("/api/v1/users/me/experiences/questions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(experienceInsightService);
    }

    @Test
    void 질문_생성이_하루_상한을_초과하면_429를_반환한다() throws Exception {
        when(experienceInsightService.generateQuestions(any(), any()))
                .thenThrow(new AiDailyLimitExceededException("경험 심층 질문·보강 요청은 하루 10회까지 가능합니다."));
        String body = """
                {"experience": {"type": "PROJECT", "title": "결제 API 서버"}}
                """;

        mockMvc.perform(post("/api/v1/users/me/experiences/questions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_EXCEEDED"));
    }

    @Test
    void 답변이_없으면_보강이_400을_반환한다() throws Exception {
        String body = """
                {"experience": {"type": "PROJECT", "title": "결제 API 서버"}, "answers": []}
                """;

        mockMvc.perform(post("/api/v1/users/me/experiences/enrich")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(experienceInsightService);
    }

    @Test
    void 답변이_1000자를_초과하면_보강이_400을_반환한다() throws Exception {
        String longAnswer = "가".repeat(1001);
        String body = String.format("""
                {"experience": {"type": "PROJECT", "title": "결제 API 서버"},
                 "answers": [{"question": "질문", "answer": "%s"}]}
                """, longAnswer);

        mockMvc.perform(post("/api/v1/users/me/experiences/enrich")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(experienceInsightService);
    }

    @Test
    void 보강이_하루_상한을_초과하면_429를_반환한다() throws Exception {
        when(experienceInsightService.enrich(any(), any()))
                .thenThrow(new AiDailyLimitExceededException("경험 심층 질문·보강 요청은 하루 10회까지 가능합니다."));
        String body = """
                {"experience": {"type": "PROJECT", "title": "결제 API 서버"},
                 "answers": [{"question": "질문", "answer": "답변"}]}
                """;

        mockMvc.perform(post("/api/v1/users/me/experiences/enrich")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_EXCEEDED"));
    }
}
