package com.career.recommendation.dto.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BE-1 — F-03 활동 추천 API 응답 DTO
 *
 * GET /api/v1/recommendations 응답 명세 준수
 */
public record RecommendationResponse(
        List<ActivityItem> activities,
        int totalCount,              // 추천 활동 총 개수
        LocalDateTime generatedAt,   // 추천 생성 시각
        String dataSource,           // "PASSER_DATA" | "AI_ONLY"
        int passerCount,             // 비교에 사용된 합격자 수
        String message               // UI 표시용 메시지
) {
    /**
     * 개별 활동 추천 항목 — API 명세서 필드 기준
     */
    public record ActivityItem(
            UUID id,             // 활동 DB ID
            String type,         // INTERNSHIP | EXTERNAL | COMPETITION
            String name,         // 활동명
            String organization, // 주관 기관
            String deadline,     // "YYYY-MM-DD" 또는 null
            int matchScore,      // 0~100
            String reason,       // 추천 이유
            String[] tags        // 태그 목록 (예: ["SW", "대기업", "무급"])
    ) {}

    // ─── static factory methods ──────────────────────────────────────────────

    public static RecommendationResponse withPasserData(List<ActivityItem> items, int passerCount) {
        return new RecommendationResponse(
                items,
                items.size(),
                LocalDateTime.now(),
                "PASSER_DATA",
                passerCount,
                passerCount + "명의 합격자 데이터를 기반으로 추천했습니다."
        );
    }

    public static RecommendationResponse aiOnly(List<ActivityItem> items) {
        return new RecommendationResponse(
                items,
                items.size(),
                LocalDateTime.now(),
                "AI_ONLY",
                0,
                "아직 유사 합격자 데이터가 부족하여 AI 일반 추천을 제공합니다."
        );
    }
}
