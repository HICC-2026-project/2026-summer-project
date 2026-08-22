package com.career.recommendation.controller;

import com.career.recommendation.dto.admin.AdminPasserReportResponse;
import com.career.recommendation.dto.admin.PasserReviewRequest;
import com.career.recommendation.service.AdminPasserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 합격자 제보 검수 API. SecurityConfig에서 /api/v1/admin/** 전체가 ROLE_ADMIN으로 묶인다
 * (관리자 계정은 ADMIN_PROVIDER_IDS 환경변수 — AdminAccountPolicy).
 */
@Tag(name = "Admin · Passer Review", description = "합격자 제보 검수 (관리자 전용)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/passers/reports")
@RequiredArgsConstructor
public class AdminPasserReportController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminPasserReportService adminPasserReportService;

    @Operation(summary = "제보 목록", description = "status=PENDING(기본)|VERIFIED|REJECTED. 사용자 제보(USER_REPORT)만 대상.")
    @GetMapping
    public Page<AdminPasserReportResponse> list(
            @Parameter(description = "PENDING | VERIFIED | REJECTED") @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return adminPasserReportService.list(status, PageRequest.of(Math.max(0, page), boundedSize));
    }

    @Operation(summary = "제보 상세")
    @GetMapping("/{id}")
    public AdminPasserReportResponse get(@PathVariable UUID id) {
        return adminPasserReportService.get(id);
    }

    @Operation(summary = "증빙 이미지", description = "검수자가 브라우저에서 바로 열어볼 수 있게 inline으로 내려준다.")
    @GetMapping("/{id}/proof")
    public ResponseEntity<Resource> proof(@PathVariable UUID id) {
        return adminPasserReportService.proof(id)
                .map(file -> ResponseEntity.ok()
                        .contentType(file.contentType() != null
                                ? MediaType.parseMediaType(file.contentType())
                                : MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(file.originalName() != null ? file.originalName() : "proof", StandardCharsets.UTF_8)
                                .build().toString())
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(file.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "승인/반려", description = "APPROVE → is_verified=true·비교 데이터 반영(프로필 캐시 즉시 무효화). REJECT → 사유 필수, 데이터는 보존.")
    @PatchMapping("/{id}")
    public AdminPasserReportResponse review(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody PasserReviewRequest request
    ) {
        return adminPasserReportService.review(authentication, id, request);
    }
}
