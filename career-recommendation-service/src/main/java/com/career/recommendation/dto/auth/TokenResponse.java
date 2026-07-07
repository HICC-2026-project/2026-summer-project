package com.career.recommendation.dto.auth;

import com.career.recommendation.service.TokenService;

public record TokenResponse(String accessToken, String refreshToken) {
    public static TokenResponse from(TokenService.TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken());
    }
}
