package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.github.GithubConnectRequest;
import com.career.recommendation.dto.github.GithubConnectResponse;
import com.career.recommendation.dto.github.GithubProfileResponse;
import com.career.recommendation.service.GithubAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * E3(1단계) — GitHub 공개 레포 기반 경험 추론(OAuth 없음). FE와 공유된 확정 계약이므로
 * 경로·필드명·상태코드를 임의로 바꾸지 않는다.
 */
@Tag(name = "GitHub 연동", description = "공개 레포 분석 기반 경험 추론 (E3-1단계, OAuth 없음)")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/users/me/github")
@RequiredArgsConstructor
public class GithubProfileController {

    private final GithubAnalysisService githubAnalysisService;

    @Operation(summary = "GitHub 분석 요청",
            description = "GitHub username 또는 github.com/{user} URL을 등록하고 공개 레포 분석을 비동기로 시작한다. " +
                    "이미 진행 중이거나 마지막 분석 후 24시간이 지나지 않았으면 409.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GithubConnectResponse connect(
            Authentication authentication,
            @Valid @RequestBody GithubConnectRequest request
    ) {
        return githubAnalysisService.connect(authentication, request);
    }

    @Operation(summary = "GitHub 분석 결과 조회",
            description = "미등록이면 connected=false만 내려준다. 등록 시 상태(PENDING/DONE/FAILED/RATE_LIMITED)와 " +
                    "직무별 비율·레포 목록·목표 직무 일치율을 포함한다.")
    @GetMapping
    public GithubProfileResponse getMyProfile(Authentication authentication) {
        return githubAnalysisService.getMyProfile(authentication);
    }

    @Operation(summary = "GitHub 연동 해제",
            description = "분석 결과를 삭제하고, 그로부터 파생된 경험(source=GITHUB)만 제거한다. 수동으로 입력한 경험은 보존한다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication) {
        githubAnalysisService.disconnect(authentication);
    }
}
