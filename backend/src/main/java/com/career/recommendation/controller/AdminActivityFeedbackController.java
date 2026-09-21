package com.career.recommendation.controller;

import com.career.recommendation.dto.admin.ActivityFeedbackSummaryResponse;
import com.career.recommendation.service.RecommendationFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * E10-2(F-09) 관리자 집계 — 활동별 추천 피드백(LIKE/DISLIKE) 수. 시드 품질 점검용(관리자 화면
 * "추천 피드백" 탭). /api/v1/admin/** 인가는 SecurityConfig. AdminOpsController의 Gemini 운영
 * 요약과는 성격이 달라 별도 엔드포인트로 둔다.
 */
@Tag(name = "Admin · 추천 피드백", description = "활동별 LIKE/DISLIKE 집계 (관리자 전용, E10-2)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/activities")
@RequiredArgsConstructor
public class AdminActivityFeedbackController {

    private final RecommendationFeedbackService recommendationFeedbackService;

    @Operation(summary = "활동별 추천 피드백 집계", description = "활동 id·이름별 LIKE/DISLIKE 수. dislike가 많은 순으로 정렬한다.")
    @GetMapping("/feedback-summary")
    public List<ActivityFeedbackSummaryResponse> feedbackSummary() {
        return recommendationFeedbackService.summarizeByActivity();
    }
}
