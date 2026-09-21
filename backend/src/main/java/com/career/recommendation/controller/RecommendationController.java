package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.recommendation.RecommendationFeedbackRequest;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.service.RecommendationFeedbackService;
import com.career.recommendation.service.RecommendationService;
import com.career.recommendation.service.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * BE-1 담당 — F-03 활동 추천 + F-05 커리어 로드맵 + F-09 추천 피드백 API.
 * 모든 엔드포인트가 JWT 인증 필수 (Spring Security 설정으로 제어).
 */
@Tag(name = "AI 추천", description = "Claude 기반 맞춤 활동 추천(F-03)과 커리어 로드맵(F-05), 추천 피드백(F-09) — 스펙·목표 직무 등록 후 사용 가능")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RoadmapService roadmapService;
    private final RecommendationFeedbackService recommendationFeedbackService;

    /**
     * F-03: AI 기반 맞춤 활동 추천 (스펙 변경 시에만 재생성, 하루 3회 제한).
     * isAiRecommendation 필드가 false이면 프론트에서 "일반 추천" 배지를 표시한다.
     */
    @Operation(summary = "맞춤 활동 추천 (F-03)",
            description = "스펙·목표 직무 기반 AI 활동 추천. 스펙을 바꾸지 않으면 저장된 결과를 그대로 반환하고, 바꾸면 하루 3회까지 새로 생성한다. isAiRecommendation이 false면 기본 추천(fallback)이다.")
    @GetMapping("/recommendations")
    public RecommendationResponse getRecommendations(Authentication authentication) {
        return recommendationService.getRecommendations(authentication);
    }

    /**
     * F-05: 학기/방학 단위로 구분된 12개월 커리어 로드맵 생성.
     */
    @Operation(summary = "커리어 로드맵 생성 (F-05)",
            description = "학기·방학 단위로 구분된 12개월 커리어 로드맵을 생성한다.")
    @GetMapping("/roadmaps")
    public RoadmapResponse getRoadmap(Authentication authentication) {
        return roadmapService.getRoadmap(authentication);
    }

    /**
     * F-09: 활동에 대한 반응(LIKE/DISLIKE) 등록 또는 변경(upsert).
     * 7/14 "유저당 1건 + 24시간 캐시" 결정을 유지 — 즉시 추천을 재생성하지 않고 다음 갱신 때 반영된다.
     */
    @Operation(summary = "추천 피드백 등록/변경 (F-09)",
            description = "활동에 대한 LIKE/DISLIKE를 등록하거나 변경한다(upsert). 존재하지 않는 activityId면 404. " +
                    "즉시 추천을 재생성하지 않고 다음 추천 갱신 때 반영된다.")
    @PostMapping("/recommendations/{activityId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitFeedback(
            Authentication authentication,
            @PathVariable UUID activityId,
            @Valid @RequestBody RecommendationFeedbackRequest request
    ) {
        recommendationFeedbackService.upsert(authentication, activityId, request);
    }

    /** F-09: 등록된 반응 해제. 같은 반응 버튼을 다시 누른 경우 FE가 이 엔드포인트를 부른다. */
    @Operation(summary = "추천 피드백 해제 (F-09)", description = "등록된 LIKE/DISLIKE를 해제한다. 존재하지 않는 activityId면 404.")
    @DeleteMapping("/recommendations/{activityId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(Authentication authentication, @PathVariable UUID activityId) {
        recommendationFeedbackService.delete(authentication, activityId);
    }
}
