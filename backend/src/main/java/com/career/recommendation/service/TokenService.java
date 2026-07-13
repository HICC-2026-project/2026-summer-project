package com.career.recommendation.service;

import com.career.recommendation.entity.RefreshToken;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.repository.RefreshTokenRepository;
import com.career.recommendation.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public TokenPair issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(buildRefreshToken(user, refreshToken));

        return new TokenPair(accessToken, refreshToken);
    }

    @Transactional
    public TokenPair reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("리프레시 토큰이 유효하지 않습니다.");
        }

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("리프레시 토큰이 존재하지 않습니다."));

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new InvalidTokenException("리프레시 토큰이 만료되었습니다.");
        }

        User user = saved.getUser();
        refreshTokenRepository.delete(saved);

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(buildRefreshToken(user, newRefreshToken));

        return new TokenPair(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    private RefreshToken buildRefreshToken(User user, String token) {
        return RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration())))
                .build();
    }
}
