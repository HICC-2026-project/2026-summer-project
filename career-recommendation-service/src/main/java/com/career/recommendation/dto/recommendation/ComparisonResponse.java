package com.career.recommendation.dto.recommendation;

import java.util.List;

/**
 * BE-1 — F-04 합격자 스펙 비교 응답 DTO
 */
public record ComparisonResponse(
        int overallMatchScore,         // 전체 matchScore (0~100)
        List<GapItem> gaps,            // 스펙 갭 목록
        List<String> strengths,        // 사용자 강점
        String dataSource,             // "PASSER_DATA" | "AI_ONLY"
        int passerCount,
        String message
) {
    public record GapItem(
            String category,           // "GPA" | "LANGUAGE" | "CERTIFICATION" | "EXPERIENCE"
            String description,        // 갭 설명
            String suggestion,         // 개선 제안
            String priority            // "HIGH" | "MEDIUM" | "LOW"
    ) {}

    public static ComparisonResponse dataInsufficient() {
        return new ComparisonResponse(
                0, List.of(), List.of(),
                "AI_ONLY", 0,
                "해당 활동의 합격자 데이터가 아직 부족합니다. AI 기반 일반 분석을 제공합니다."
        );
    }
}
