package com.career.recommendation.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository();

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
}
