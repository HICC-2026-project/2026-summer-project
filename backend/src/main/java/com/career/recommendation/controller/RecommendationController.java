package com.career.recommendation.controller;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.service.RecommendationService;
import com.career.recommendation.service.RoadmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BE-1 담당 — F-03 활동 추천 + F-05 커리어 로드맵 API.
 * 두 엔드포인트 모두 JWT 인증 필수 (Spring Security 설정으로 제어).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RoadmapService roadmapService;

    /**
     * F-03: Claude AI 기반 맞춤 활동 추천 (24시간 캐싱 적용).
     * isAiRecommendation 필드가 false이면 프론트에서 "일반 추천" 배지를 표시한다.
     */
    @GetMapping("/recommendations")
    public RecommendationResponse getRecommendations(Authentication authentication) {
        return recommendationService.getRecommendations(authentication);
    }

    /**
     * F-05: 학기/방학 단위로 구분된 6개월 커리어 로드맵 생성.
     */
    @GetMapping("/roadmaps")
    public RoadmapResponse getRoadmap(Authentication authentication) {
        return roadmapService.getRoadmap(authentication);
    }
}
