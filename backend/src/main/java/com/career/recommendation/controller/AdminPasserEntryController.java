package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.admin.AdminPasserEntryRequest;
import com.career.recommendation.dto.admin.AdminPasserEntryResponse;
import com.career.recommendation.service.AdminPasserEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 합격자 수기 등록 API. SecurityConfig에서 /api/v1/admin/** 전체가 ROLE_ADMIN으로
 * 묶인다(관리자 계정은 ADMIN_PROVIDER_IDS 환경변수 — AdminAccountPolicy).
 */
@Tag(name = "Admin · Passer Manual Entry", description = "합격자 데이터 수기 등록 (관리자 전용)")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/admin/passers")
@RequiredArgsConstructor
public class AdminPasserEntryController {

    private final AdminPasserEntryService adminPasserEntryService;

    @Operation(
            summary = "합격자 데이터 수기 등록",
            description = "공개된 커뮤니티·블로그 합격 스펙 후기를 관리자가 직접 등록한다. "
                    + "증빙·검수 절차 없이 저장 즉시 isVerified=true로 비교 데이터에 반영된다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPasserEntryResponse create(
            Authentication authentication,
            @Valid @RequestBody AdminPasserEntryRequest request
    ) {
        return adminPasserEntryService.create(authentication, request);
    }
}
