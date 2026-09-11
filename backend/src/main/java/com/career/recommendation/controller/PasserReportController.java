package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.passer.MyPasserReportResponse;
import com.career.recommendation.dto.passer.PasserReportRequest;
import com.career.recommendation.dto.passer.PasserReportResponse;
import com.career.recommendation.service.PasserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "합격자 제보", description = "로그인 사용자의 익명 합격 스펙 제보")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/passers/reports")
@RequiredArgsConstructor
public class PasserReportController {

    private final PasserReportService passerReportService;

    @Operation(
            summary = "합격자 스펙 제보",
            description = "제보를 미검수(USER_REPORT) 상태로 저장한다. 팀 검수 전에는 추천·비교 데이터로 사용하지 않는다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PasserReportResponse submit(
            Authentication authentication,
            @Valid @RequestPart("request") PasserReportRequest request,
            @RequestPart("proof") MultipartFile proof
    ) {
        return passerReportService.submit(authentication, request, proof);
    }

    @Operation(
            summary = "내 제보 목록",
            description = "로그인 사용자가 제보한 합격자 데이터의 검수 상태(PENDING/VERIFIED)를 최신순으로 돌려준다."
    )
    @GetMapping("/me")
    public List<MyPasserReportResponse> myReports(Authentication authentication) {
        return passerReportService.findMyReports(authentication);
    }
}
