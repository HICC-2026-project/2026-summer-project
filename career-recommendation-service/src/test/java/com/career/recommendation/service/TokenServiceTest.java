package com.career.recommendation.service;

import com.career.recommendation.entity.User;
import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.repository.RefreshTokenRepository;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User createUser() {
        return userRepository.save(User.builder()
                .email("test-" + System.nanoTime() + "@example.com")
                .nickname("tester")
                .provider("KAKAO")
                .providerId("provider-id-" + System.nanoTime())
                .build());
    }

    @Test
    void issueTokens_발급된_토큰은_유효하고_유저정보를_담는다() {
        User user = createUser();

        TokenService.TokenPair pair = tokenService.issueTokens(user);

        assertThat(jwtTokenProvider.validateToken(pair.accessToken())).isTrue();
        assertThat(jwtTokenProvider.validateToken(pair.refreshToken())).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(pair.accessToken())).isEqualTo(user.getId());
        assertThat(jwtTokenProvider.getRoleFromToken(pair.accessToken())).isEqualTo("USER");
        assertThat(refreshTokenRepository.findByToken(pair.refreshToken())).isPresent();
    }

    @Test
    void reissue_유효한_리프레시_토큰이면_새_토큰쌍을_발급하고_기존_토큰은_폐기한다() {
        User user = createUser();
        TokenService.TokenPair original = tokenService.issueTokens(user);

        TokenService.TokenPair reissued = tokenService.reissue(original.refreshToken());

        assertThat(jwtTokenProvider.validateToken(reissued.accessToken())).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(reissued.accessToken())).isEqualTo(user.getId());
        assertThat(refreshTokenRepository.findByToken(original.refreshToken())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(reissued.refreshToken())).isPresent();
    }

    @Test
    void reissue_저장되지_않은_리프레시_토큰이면_예외() {
        User user = createUser();
        String rogueRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        assertThatThrownBy(() -> tokenService.reissue(rogueRefreshToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void reissue_변조된_토큰이면_예외() {
        assertThatThrownBy(() -> tokenService.reissue("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void revoke_리프레시_토큰을_삭제한다() {
        User user = createUser();
        TokenService.TokenPair pair = tokenService.issueTokens(user);

        tokenService.revoke(pair.refreshToken());

        assertThat(refreshTokenRepository.findByToken(pair.refreshToken())).isEmpty();
    }
}
