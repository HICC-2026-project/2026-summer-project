package com.career.recommendation.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private static MockEnvironment withProfile(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.addActiveProfile(profile);
        return env;
    }

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository(withProfile("prod"));

    @Test
    void 저장한_authorization_request를_쿠키에서_그대로_복원한다() {
        OAuth2AuthorizationRequest original = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("test-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/kakao")
                .scopes(Set.of("profile_nickname"))
                .state("state-value")
                .build();

        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookies());

        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(loadRequest);

        assertThat(restored).isNotNull();
        assertThat(restored.getState()).isEqualTo("state-value");
        assertThat(restored.getClientId()).isEqualTo("test-client");
        assertThat(restored.getScopes()).containsExactly("profile_nickname");
    }

    @Test
    void local_프로파일이_아니면_Secure_쿠키로_내려간다() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                        .clientId("test-client")
                        .redirectUri("http://localhost:8080/login/oauth2/code/kakao")
                        .state("s")
                        .build(),
                new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void local_프로파일이면_Secure를_강제하지_않는다() {
        // local(평문 HTTP 개발)에서 Secure를 강제하면 브라우저가 쿠키를 아예 안 보내
        // 카카오 로그인 왕복 자체가 깨진다.
        HttpCookieOAuth2AuthorizationRequestRepository localRepository =
                new HttpCookieOAuth2AuthorizationRequestRepository(withProfile("local"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        localRepository.saveAuthorizationRequest(
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                        .clientId("test-client")
                        .redirectUri("http://localhost:8080/login/oauth2/code/kakao")
                        .state("s")
                        .build(),
                new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

        assertThat(cookie.getSecure()).isFalse();
    }
}
