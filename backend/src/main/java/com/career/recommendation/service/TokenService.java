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
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            // refresh_tokens 테이블 조회(DB 존재 여부)가 이미 액세스 토큰을 걸러내긴 하지만
            // (액세스 토큰은 이 테이블에 저장된 적이 없다), typ 클레임으로도 명시적으로
            // 막아 두 겹으로 방어한다 — DB 조회 로직이 나중에 바뀌어도 이 방어선은 남는다.
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
