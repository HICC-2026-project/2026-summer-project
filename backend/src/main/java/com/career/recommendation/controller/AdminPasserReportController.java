package com.career.recommendation.controller;

import com.career.recommendation.domain.ReviewStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    private final AdminPasserReportService adminPasserReportService;

    /**
     * 함수 이름: list()
     * 기능: 검수 상태별 합격자 제보 목록을 페이지로 조회한다. 사용자 제보(USER_REPORT)만 대상이며
     *       페이지 크기 상한은 spring.data.web.pageable.max-page-size를 따른다.
     *       GET /api/v1/admin/passers/reports, 관리자 전용(ROLE_ADMIN).
     * 파라미터: ReviewStatus status -> 검수 상태(PENDING | VERIFIED | REJECTED, 기본 PENDING)
     *           Pageable pageable -> 페이지 정보(기본 size 20)
     * 반환값: Page<AdminPasserReportResponse> -> 제보 목록 페이지
     */
    @Operation(summary = "제보 목록", description = "status=PENDING(기본)|VERIFIED|REJECTED, page·size 쿼리. 사용자 제보(USER_REPORT)만 대상. 페이지 크기 상한은 spring.data.web.pageable.max-page-size.")
    @GetMapping
    public Page<AdminPasserReportResponse> list(
            @Parameter(description = "PENDING | VERIFIED | REJECTED") @RequestParam(defaultValue = "PENDING") ReviewStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminPasserReportService.list(status, pageable);
    }

    /**
     * 함수 이름: get()
     * 기능: 합격자 제보 한 건의 상세 정보를 조회한다.
     *       GET /api/v1/admin/passers/reports/{id}, 관리자 전용(ROLE_ADMIN).
     * 파라미터: UUID id -> 조회할 제보 ID
     * 반환값: AdminPasserReportResponse -> 제보 상세 응답
     */
    @Operation(summary = "제보 상세")
    @GetMapping("/{id}")
    public AdminPasserReportResponse get(@PathVariable UUID id) {
        return adminPasserReportService.get(id);
    }

    /**
     * 함수 이름: proof()
     * 기능: 제보에 첨부된 증빙 이미지를 검수자가 브라우저에서 바로 열어볼 수 있게 inline으로 내려준다.
     *       Content-Type은 저장된 값(없으면 octet-stream), 캐시는 private/no-store, 증빙이 없으면 404.
     *       GET /api/v1/admin/passers/reports/{id}/proof, 관리자 전용(ROLE_ADMIN).
     * 파라미터: UUID id -> 증빙을 조회할 제보 ID
     * 반환값: ResponseEntity<Resource> -> 증빙 파일 본문(inline) 또는 404 응답
     */
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

    /**
     * 함수 이름: review()
     * 기능: 제보를 승인 또는 반려한다. APPROVE는 is_verified=true로 비교 데이터에 반영(프로필 캐시 즉시 무효화),
     *       REJECT는 사유 필수이며 데이터는 보존한다.
     *       PATCH /api/v1/admin/passers/reports/{id}, 관리자 전용(ROLE_ADMIN).
     * 파라미터: Authentication authentication -> 현재 로그인한 관리자(검수자) 인증 정보
     *           UUID id -> 검수할 제보 ID
     *           PasserReviewRequest request -> 승인/반려 결정과 반려 사유(@Valid 검증)
     * 반환값: AdminPasserReportResponse -> 검수 처리 후 제보 응답
     */
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
