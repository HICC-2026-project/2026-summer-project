package com.career.recommendation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 세션을 쓰지 않는 STATELESS 설정에서는 기본 HttpSessionOAuth2AuthorizationRequestRepository가
 * 카카오 리다이렉트 왕복 사이의 authorization request를 저장할 세션이 없어 동작하지 않는다.
 * 대신 쿠키에 저장해 콜백까지 들고 온다.
 * JDK 직렬화(SerializationUtils)는 쿠키 값이 위조될 경우 역직렬화 RCE로 이어질 수 있어 쓰지 않고,
 * Spring Security가 제공하는 Jackson 모듈로 JSON 직렬화한다.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    private final ObjectMapper objectMapper = createObjectMapper();
    private final Environment environment;

    public HttpCookieOAuth2AuthorizationRequestRepository(Environment environment) {
        this.environment = environment;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader classLoader = HttpCookieOAuth2AuthorizationRequestRepository.class.getClassLoader();
        mapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        mapper.registerModule(new OAuth2ClientJackson2Module());
        return mapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }
        addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return authorizationRequest;
    }

    private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(name))
                .findFirst();
    }

    /**
     * ⚠️ HttpOnly만 있고 Secure·SameSite가 없었다. Secure가 없으면 HTTPS 배포 환경에서도
     * 평문 HTTP 요청이 한 번이라도 발생하면(리다이렉트 체인 중 실수 등) 이 쿠키(=OAuth state)가
     * 노출될 수 있다. state 자체의 CSRF 방어(Spring이 생성해 콜백에서 대조)는 이 쿠키가 있어야
     * 성립하므로, 쿠키 유출은 곧 state 방어 우회로 이어진다.
     *
     * SameSite=Lax는 항상 붙인다 — 카카오→백엔드 콜백은 top-level GET 리다이렉트라 Lax에서도
     * 정상 왕복된다. Secure는 local 프로파일(평문 HTTP로 개발)에서만 끈다 — local에서 Secure를
     * 강제하면 브라우저가 쿠키를 아예 안 보내 로그인 자체가 깨진다.
     */
    private void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        if (!environment.matchesProfiles("local")) {
            cookie.setSecure(true);
        }
        response.addCookie(cookie);
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(authorizationRequest);
            return Base64.getUrlEncoder().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest 직렬화에 실패했습니다.", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cookie.getValue());
            return objectMapper.readValue(new String(json, StandardCharsets.UTF_8), OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            return null;
        }
    }
}
