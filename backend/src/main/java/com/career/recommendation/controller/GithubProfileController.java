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

    /**
     * 함수 이름: connect()
     * 기능: GitHub username 또는 github.com/{user} URL을 등록하고 공개 레포 분석을 비동기로 시작한다.
     *       이미 진행 중이거나 마지막 분석 후 24시간이 지나지 않았으면 409.
     *       POST /api/v1/users/me/github (202 Accepted), 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           GithubConnectRequest request -> GitHub username 또는 URL(@Valid 검증)
     * 반환값: GithubConnectResponse -> 분석 시작(접수) 응답
     * 예외: 분석이 진행 중이거나 24시간 이내 재요청이면 409
     */
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

    /**
     * 함수 이름: getMyProfile()
     * 기능: 내 GitHub 분석 결과를 조회한다. 미등록이면 connected=false만 내려주고, 등록 시 상태
     *       (PENDING/DONE/FAILED/RATE_LIMITED)와 직무별 비율·레포 목록·목표 직무 일치율을 포함한다.
     *       GET /api/v1/users/me/github, 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     * 반환값: GithubProfileResponse -> GitHub 분석 결과(미등록이면 connected=false)
     */
    @Operation(summary = "GitHub 분석 결과 조회",
            description = "미등록이면 connected=false만 내려준다. 등록 시 상태(PENDING/DONE/FAILED/RATE_LIMITED)와 " +
                    "직무별 비율·레포 목록·목표 직무 일치율을 포함한다.")
    @GetMapping
    public GithubProfileResponse getMyProfile(Authentication authentication) {
        return githubAnalysisService.getMyProfile(authentication);
    }

    /**
     * 함수 이름: disconnect()
     * 기능: GitHub 분석 결과를 삭제하고, 그로부터 파생된 경험(source=GITHUB)만 제거한다.
     *       수동으로 입력한 경험은 보존한다.
     *       DELETE /api/v1/users/me/github (204 No Content), 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     * 반환값: 없음
     */
    @Operation(summary = "GitHub 연동 해제",
            description = "분석 결과를 삭제하고, 그로부터 파생된 경험(source=GITHUB)만 제거한다. 수동으로 입력한 경험은 보존한다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication) {
        githubAnalysisService.disconnect(authentication);
    }
}
