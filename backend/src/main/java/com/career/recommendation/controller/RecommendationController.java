package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.service.RecommendationService;
import com.career.recommendation.service.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BE-1 담당 — F-03 활동 추천 + F-05 커리어 로드맵 API.
 * 두 엔드포인트 모두 JWT 인증 필수 (Spring Security 설정으로 제어).
 */
@Tag(name = "AI 추천", description = "Claude 기반 맞춤 활동 추천(F-03)과 커리어 로드맵(F-05) — 스펙·목표 직무 등록 후 사용 가능")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
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
    @Operation(summary = "맞춤 활동 추천 (F-03)",
            description = "스펙·목표 직무 기반 AI 활동 추천. 결과는 24시간 캐싱되며, isAiRecommendation이 false면 기본 추천(fallback)이다.")
    @GetMapping("/recommendations")
    public RecommendationResponse getRecommendations(Authentication authentication) {
        return recommendationService.getRecommendations(authentication);
    }

    /**
     * F-05: 학기/방학 단위로 구분된 6개월 커리어 로드맵 생성.
     */
    @Operation(summary = "커리어 로드맵 생성 (F-05)",
            description = "학기·방학 단위로 구분된 6개월 커리어 로드맵을 생성한다.")
    @GetMapping("/roadmaps")
    public RoadmapResponse getRoadmap(Authentication authentication) {
        return roadmapService.getRoadmap(authentication);
    }
}
