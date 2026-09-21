package com.career.recommendation.controller;

import com.career.recommendation.exception.ActivityNotFoundException;
import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.RecommendationFeedbackService;
import com.career.recommendation.service.RecommendationService;
import com.career.recommendation.service.RoadmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler의 새 ResponseStatusException 핸들러가 ActivityController 외의
 * 다른 컨트롤러(RecommendationController가 위임하는 RecommendationService·RoadmapService)에서
 * CurrentUserService가 던지는 401/404에도 동일하게 적용되는지 확인한다.
 */
@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private RoadmapService roadmapService;

    @MockBean
    private RecommendationFeedbackService recommendationFeedbackService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 추천_조회시_인증_정보가_없으면_401을_반환한다() throws Exception {
        when(recommendationService.getRecommendations(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다."));

        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증 정보가 없습니다."));
    }

    @Test
    void 추천_조회시_사용자가_없으면_404를_반환한다() throws Exception {
        when(recommendationService.getRecommendations(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자가 존재하지 않습니다."));

        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자가 존재하지 않습니다."));
    }

    @Test
    void 로드맵_조회시_인증_정보가_없으면_401을_반환한다() throws Exception {
        when(roadmapService.getRoadmap(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다."));

        mockMvc.perform(get("/api/v1/roadmaps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증 정보가 없습니다."));
    }

    @Test
    void 로드맵_조회시_사용자가_없으면_404를_반환한다() throws Exception {
        when(roadmapService.getRoadmap(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자가 존재하지 않습니다."));

        mockMvc.perform(get("/api/v1/roadmaps"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자가 존재하지 않습니다."));
    }

    @Test
    void 피드백_등록시_존재하지_않는_활동이면_404를_반환한다() throws Exception {
        UUID activityId = UUID.randomUUID();
        doThrow(new ActivityNotFoundException(activityId))
                .when(recommendationFeedbackService).upsert(any(), org.mockito.ArgumentMatchers.eq(activityId), any());

        mockMvc.perform(post("/api/v1/recommendations/{activityId}/feedback", activityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void 피드백_등록시_reaction이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/{activityId}/feedback", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 피드백_등록시_알_수_없는_reaction_값이면_400을_반환한다() throws Exception {
        // LIKE/DISLIKE가 아닌 값은 enum 역직렬화 단계에서 HttpMessageNotReadableException → 400.
        mockMvc.perform(post("/api/v1/recommendations/{activityId}/feedback", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"NEUTRAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 피드백_해제시_존재하지_않는_활동이면_404를_반환한다() throws Exception {
        UUID activityId = UUID.randomUUID();
        doThrow(new ActivityNotFoundException(activityId))
                .when(recommendationFeedbackService).delete(any(), org.mockito.ArgumentMatchers.eq(activityId));

        mockMvc.perform(delete("/api/v1/recommendations/{activityId}/feedback", activityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void 피드백_등록이_정상이면_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/{activityId}/feedback", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"DISLIKE\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 피드백_해제가_정상이면_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/recommendations/{activityId}/feedback", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
