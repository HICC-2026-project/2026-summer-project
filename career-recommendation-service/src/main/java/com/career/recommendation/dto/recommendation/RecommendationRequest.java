package com.career.recommendation.dto.recommendation;

import java.util.List;

/**
 * BE-1 — F-03 활동 추천 API 요청 DTO
 */
public record RecommendationRequest(
        String targetJob,   // 목표 직무 (예: "백엔드 개발자", "데이터 엔지니어")
        String activityType // 필터: INTERNSHIP | EXTERNAL | COMPETITION | null (전체)
) {}
