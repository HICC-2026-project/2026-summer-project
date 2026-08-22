package com.career.recommendation.service;

import com.career.recommendation.entity.User;
import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.repository.RefreshTokenRepository;
import com.career.recommendation.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 의도적으로 클래스 레벨 @Transactional을 걸지 않는다 — 테스트 트랜잭션에 합류하면 서비스가 던진
 * 예외로 인한 롤백이 보이지 않아, "세션을 폐기했다"는 단언이 운영에서는 거짓이 될 수 있다
 * (실제로 noRollbackFor 없이 통과하던 테스트가 있었다). 여기서는 각 호출이 실제로 커밋된다.
 */
@SpringBootTest
@ActiveProfiles("local")
class TokenServiceReuseTest {

    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId != null) {
            userRepository.deleteById(userId); // refresh_tokens는 FK CASCADE
        }
    }

    @Test
    void 재사용_감지로_폐기한_세션은_예외가_나가도_DB에_커밋된다() {
        User user = userRepository.save(User.builder()
                .nickname("reuse").provider("KAKAO").providerId("reuse-" + System.nanoTime()).build());
        userId = user.getId();
        TokenService.TokenPair original = tokenService.issueTokens(user);
        TokenService.TokenPair rotated = tokenService.reissue(original.refreshToken());

        assertThatThrownBy(() -> tokenService.reissue(original.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);

        // 별도 트랜잭션에서 봐도 새 토큰이 사라져 있어야 한다(롤백됐다면 남아 있다)
        assertThat(refreshTokenRepository.findByToken(rotated.refreshToken())).isEmpty();
    }
}
