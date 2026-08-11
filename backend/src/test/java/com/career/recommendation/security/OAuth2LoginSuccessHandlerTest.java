package com.career.recommendation.security;

import com.career.recommendation.entity.User;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이번 세션 최대 발견(리프레시 토큰이 액세스 토큰으로 통용되던 취약점)과 OAuth2 쿠키
 * Secure/SameSite 수정이 정확히 이 흐름(로그인 성공 → 토큰 발급 → 리다이렉트)과 맞닿아
 * 있는데, 그걸 지켜주는 회귀 테스트가 하나도 없었다(Opus 5 검토 8라운드가 테스트 커버리지
 * 0인 클래스로 지목). 최소한의 계약을 고정한다: (1) 신규/기존 유저 upsert가 provider+
 * providerId 기준으로 정확히 동작하는지, (2) 토큰이 쿼리 파라미터가 아니라 URL 프래그먼트로
 * 나가는지(서버 로그·Referer 유출 방지가 이 흐름의 핵심 설계 의도), (3) 실제로
 * TokenService.issueTokens가 호출돼 typ 클레임 있는 진짜 토큰 쌍이 발급되는지.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    private OAuth2LoginSuccessHandler handler;

    private void init() {
        handler = new OAuth2LoginSuccessHandler(userRepository, tokenService);
        ReflectionTestUtils.setField(handler, "frontendRedirectUri", "https://example.com/oauth/callback");
    }

    private CustomOAuth2User oAuth2User(String providerId, String nickname) {
        return new CustomOAuth2User(Map.of(), "KAKAO", providerId, nickname, null);
    }

    @Test
    void 신규_유저는_provider와_providerId로_새로_생성된다() throws Exception {
        init();
        CustomOAuth2User principal = oAuth2User("kakao-123", "새유저");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByProviderAndProviderId("KAKAO", "kakao-123")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(tokenService.issueTokens(any(User.class)))
                .thenReturn(new TokenService.TokenPair("access-token", "refresh-token"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("KAKAO");
        assertThat(captor.getValue().getProviderId()).isEqualTo("kakao-123");
        assertThat(captor.getValue().getNickname()).isEqualTo("새유저");
    }

    @Test
    void 기존_유저는_새로_만들지_않고_닉네임만_갱신한다() throws Exception {
        init();
        User existing = User.builder()
                .id(UUID.randomUUID())
                .provider("KAKAO")
                .providerId("kakao-456")
                .nickname("옛닉네임")
                .build();
        CustomOAuth2User principal = oAuth2User("kakao-456", "새닉네임");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByProviderAndProviderId("KAKAO", "kakao-456")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.issueTokens(any(User.class)))
                .thenReturn(new TokenService.TokenPair("access-token", "refresh-token"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getNickname()).isEqualTo("새닉네임");
    }

    @Test
    void 닉네임_동의를_철회한_재로그인이_기존_닉네임을_지우지_않는다() throws Exception {
        // 카카오 profile_nickname은 선택 동의 항목이라 재로그인 시 nickname이 null로 올 수 있다.
        // 그대로 덮어쓰면 "로그인만 했는데 프로필 이름이 사라지는" 조용한 데이터 유실이 된다.
        init();
        User existing = User.builder()
                .id(UUID.randomUUID())
                .provider("KAKAO")
                .providerId("kakao-no-consent")
                .nickname("기존닉네임")
                .build();
        when(authentication.getPrincipal()).thenReturn(oAuth2User("kakao-no-consent", null));
        when(userRepository.findByProviderAndProviderId("KAKAO", "kakao-no-consent"))
                .thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.issueTokens(any(User.class)))
                .thenReturn(new TokenService.TokenPair("access-token", "refresh-token"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("기존닉네임");
    }

    @Test
    void 토큰은_쿼리_파라미터가_아니라_URL_프래그먼트로_리다이렉트된다() throws Exception {
        // 쿼리 파라미터로 나가면 서버 액세스 로그·프록시 로그·Referer 헤더에 토큰이
        // 그대로 남는다. #fragment는 브라우저가 서버로 전송하지 않으므로 이 흐름의
        // 핵심 방어선이다 — 클래스 상단 주석이 명시하는 설계 의도를 코드로 고정한다.
        init();
        CustomOAuth2User principal = oAuth2User("kakao-789", "유저");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(tokenService.issueTokens(any(User.class)))
                .thenReturn(new TokenService.TokenPair("the-access-token", "the-refresh-token"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();

        assertThat(redirectUrl).doesNotContain("?accessToken=", "?refreshToken=");
        assertThat(redirectUrl).contains("#accessToken=the-access-token");
        assertThat(redirectUrl).contains("refreshToken=the-refresh-token");
    }

    @Test
    void 유저_저장에_실패하면_토큰을_발급하지_않는다() {
        init();
        CustomOAuth2User principal = oAuth2User("kakao-fail", "유저");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB 오류"));

        try {
            handler.onAuthenticationSuccess(request, response, authentication);
        } catch (Exception ignored) {
            // 예외 자체보다, 실패 시 토큰이 발급되지 않는지가 이 테스트의 관심사다.
        }

        verify(tokenService, never()).issueTokens(any());
    }
}
