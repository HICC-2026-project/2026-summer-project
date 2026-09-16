package com.career.recommendation.controller;

import com.career.recommendation.dto.admin.OpsSummaryResponse;
import com.career.recommendation.service.OpsSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 요약(관리자 전용). /api/v1/admin/** 인가는 SecurityConfig. */
@Tag(name = "Admin · Ops", description = "운영 요약 (관리자 전용)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/ops")
@RequiredArgsConstructor
public class AdminOpsController {

    private final OpsSummaryService opsSummaryService;

    @Operation(summary = "운영 요약", description = "검수 대기 수, 직무별 비교 가능 합격자 수, Gemini 호출 통계(오늘 사용량·누적 성공/실패·지연).")
    @GetMapping("/summary")
    public OpsSummaryResponse summary() {
        return opsSummaryService.summarize();
    }
}
