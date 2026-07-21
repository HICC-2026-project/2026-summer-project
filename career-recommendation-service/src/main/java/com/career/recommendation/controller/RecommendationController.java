package com.career.recommendation.controller;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ※ 컨트롤러 레이어 담당: BE-3 (이지우)
 *   BE-1은 RecommendationService 인터페이스만 제공
 *   실제 Controller 세부 구현(인증 방식 등)은 BE-3이 확정 후 수정
 *
 * 엔드포인트:
 *   GET /api/v1/recommendations        — 맞춤 활동 추천 목록 (F-03)
 *   GET /api/v1/recommendations/{id}   — 추천 상세 + 이유     (BE-3 추가 예정)
 *   POST /api/v1/recommendations/{id}/feedback — 피드백       (BE-3 추가 예정)
 *
 * 인증: JWT Bearer Token (필수)
 */
@Tag(name = "추천", description = "AI 기반 활동 추천 API (F-03)")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * GET /api/v1/recommendations
     * 맞춤 활동 추천 목록 조회 (F-03)
     *
     * @param targetJob 목표 직무 쿼리 파라미터 (예: ?targetJob=백엔드 개발자)
     *                  — TargetJob Entity 연동 전 임시로 쿼리 파라미터 수신
     *                  — BE-2 F-02 완료 후 /users/me/target 에서 자동 조회로 전환 예정
     */
    @Operation(
            summary = "맞춤 활동 추천 목록",
            description = "사용자 스펙 + 유사 합격자 데이터를 기반으로 인턴십·대외활동·공모전을 추천합니다. " +
                          "합격자 데이터가 3건 미만이면 AI 일반 추천을 제공합니다."
    )
    @GetMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "목표 직무 (예: 백엔드 개발자, 데이터 엔지니어)")
            @RequestParam(required = false, defaultValue = "IT 개발직") String targetJob
    ) {
        // NOTE: BE-3 JwtAuthenticationFilter 방식 확정 후 userId 추출 수정 필요
        // 현재: userDetails.getUsername() = userId(UUID 문자열) 가정
        UUID userId = UUID.fromString(userDetails.getUsername());
        RecommendationResponse response = recommendationService.recommend(userId, targetJob);
        return ResponseEntity.ok(response);
    }

    // ─── 아래 엔드포인트는 BE-3 담당 ────────────────────────────────────────
    // GET  /recommendations/{id}          → 추천 상세
    // POST /recommendations/{id}/feedback → 피드백
    // (BE-3이 별도 구현)
}

