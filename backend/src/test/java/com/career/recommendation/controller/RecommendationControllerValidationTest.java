package com.career.recommendation.controller;

import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.RecommendationService;
import com.career.recommendation.service.RoadmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
