package com.career.recommendation.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository();

    private static OAuth2AuthorizationRequest sampleRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("test-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/kakao")
                .scopes(Set.of("profile_nickname"))
                .state(state)
                .build();
    }

    @Test
    void 저장한_authorization_request를_쿠키에서_그대로_복원한다() {
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest("state-value"), saveRequest, saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookies());

        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(loadRequest);

        assertThat(restored).isNotNull();
        assertThat(restored.getState()).isEqualTo("state-value");
        assertThat(restored.getClientId()).isEqualTo("test-client");
        assertThat(restored.getScopes()).containsExactly("profile_nickname");
    }

    /**
     * ⚠️ 이전엔 "local 프로파일이 아니면 Secure를 켠다"였는데, 프로덕션이 prod 프로파일이면서도
     * 평문 HTTP(탄력적 IP 직접 접근, 인증서 없음)라서 브라우저가 Secure 쿠키를 조용히 버렸고,
     * 카카오 콜백에 state 쿠키가 없어 전원 login_failed가 됐다(2026-08-11 실제 장애).
     * 프로파일이 아니라 실제 요청 프로토콜(request.isSecure()) 기준으로 판단해야 한다.
     */
    @Test
    void HTTP_요청이면_프로파일과_무관하게_Secure를_붙이지_않는다() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest(); // 기본 scheme=http
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest("s"), httpRequest, response);

        Cookie cookie = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

        // HTTP에서 Secure를 붙이면 브라우저가 쿠키 저장을 거부해 로그인 왕복이 깨진다.
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void HTTPS_요청이면_Secure_쿠키로_내려간다() {
        MockHttpServletRequest httpsRequest = new MockHttpServletRequest();
        httpsRequest.setScheme("https");
        httpsRequest.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest("s"), httpsRequest, response);

        Cookie cookie = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }
}
