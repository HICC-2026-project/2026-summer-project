package com.career.recommendation.dto.auth;

import com.career.recommendation.service.TokenService;

public record TokenResponse(String accessToken, String refreshToken) {
    /**
     * 함수 이름: from()
     * 기능: TokenService가 발급한 토큰 쌍에서 액세스·리프레시 토큰을 꺼내 응답 DTO로 만든다.
     * 파라미터: TokenService.TokenPair pair -> 발급된 액세스/리프레시 토큰 쌍
     * 반환값: TokenResponse -> 두 토큰을 담은 응답 DTO
     */
    public static TokenResponse from(TokenService.TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken());
    }
}
