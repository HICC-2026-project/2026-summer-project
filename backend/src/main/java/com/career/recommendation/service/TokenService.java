package com.career.recommendation.service;

import com.career.recommendation.entity.RefreshToken;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.repository.RefreshTokenRepository;
import com.career.recommendation.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
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
                .orElseThrow(() -> {
                    // 서명·만료·typ이 모두 정상인데 DB에 없다 = 이미 로테이션으로 폐기된 토큰의 재사용.
                    // 정상 클라이언트는 교체된 새 토큰만 들고 있으므로, 옛 토큰이 다시 오면 둘 중 하나다:
                    // (a) 탈취자가 먼저 썼고 지금 온 게 정상 사용자, (b) 정상 사용자가 먼저 썼고 지금 온 게 탈취자.
                    // 어느 쪽인지 구분할 수 없으므로 그 사용자의 모든 리프레시 토큰을 폐기해 양쪽 다 재로그인시킨다
                    // (OAuth 2.0 Security BCP의 refresh token rotation 권고와 같은 대응).
                    UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
                    int revoked = refreshTokenRepository.deleteByUserId(userId);
                    log.warn("리프레시 토큰 재사용 감지: user={}, 폐기된 세션 {}건 — 전체 재로그인 필요", userId, revoked);
                    return new InvalidTokenException("리프레시 토큰이 재사용되어 모든 세션을 종료했습니다. 다시 로그인해 주세요.");
                });

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
