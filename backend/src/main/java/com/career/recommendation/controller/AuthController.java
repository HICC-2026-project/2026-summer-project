package com.career.recommendation.controller;

import com.career.recommendation.dto.auth.RefreshTokenRequest;
import com.career.recommendation.dto.auth.TokenResponse;
import com.career.recommendation.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "토큰 재발급 및 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;

    /**
     * 함수 이름: refresh()
     * 기능: 리프레시 토큰으로 새 액세스·리프레시 토큰 쌍을 발급한다. 기존 리프레시 토큰은 폐기된다(로테이션).
     *       POST /api/v1/auth/refresh, 로그인(액세스 토큰) 없이 호출.
     * 파라미터: RefreshTokenRequest request -> 리프레시 토큰을 담은 요청(@Valid 검증)
     * 반환값: ResponseEntity<TokenResponse> -> 새 토큰 쌍(200 OK)
     * 예외: 리프레시 토큰이 만료됐거나 유효하지 않으면 401
     */
    @Operation(summary = "액세스 토큰 재발급",
            description = "리프레시 토큰으로 새 액세스·리프레시 토큰 쌍을 발급한다. 기존 리프레시 토큰은 폐기됨(로테이션).")
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "401", description = "리프레시 토큰이 만료됐거나 유효하지 않음")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenService.TokenPair pair = tokenService.reissue(request.refreshToken());
        return ResponseEntity.ok(TokenResponse.from(pair));
    }

    /**
     * 함수 이름: logout()
     * 기능: 리프레시 토큰을 폐기한다. 이후 해당 토큰으로는 재발급할 수 없다.
     *       POST /api/v1/auth/logout
     * 파라미터: RefreshTokenRequest request -> 폐기할 리프레시 토큰을 담은 요청(@Valid 검증)
     * 반환값: ResponseEntity<Void> -> 본문 없는 204 No Content
     */
    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기한다. 이후 해당 토큰으로는 재발급 불가.")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
