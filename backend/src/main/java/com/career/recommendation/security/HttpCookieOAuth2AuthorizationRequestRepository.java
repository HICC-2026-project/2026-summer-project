package com.career.recommendation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        addCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
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
     * SameSite=Lax는 항상 붙인다 — 카카오→백엔드 콜백은 top-level GET 리다이렉트라 Lax에서도
     * 정상 왕복된다.
     *
     * ⚠️ Secure는 "프로파일이 local이 아니면 켠다"로 판단하면 안 된다. 실제로 그렇게 배포했다가
     * 프로덕션 카카오 로그인이 전면 불능이 된 적이 있다(2026-08-11): prod 프로파일이지만 서버가
     * 아직 평문 HTTP(탄력적 IP 직접 접근, 도메인·인증서 없음)라서, 브라우저가 Secure 쿠키를
     * 조용히 버렸다 → 카카오에서 돌아온 콜백에 state 쿠키가 없음 → authorization request 조회
     * 실패 → 전원 login_failed. 프로파일은 접속 프로토콜을 보장하지 않으므로, 지금 이 요청이
     * 실제로 HTTPS로 왔는지(request.isSecure())를 기준으로 켠다 — HTTP 요청에 Secure를 붙이는
     * 것은 어차피 브라우저가 저장을 거부하니 보호 효과도 없다. 나중에 HTTPS(도메인+인증서 또는
     * ALB)로 전환하면 별도 수정 없이 Secure가 자동으로 켜진다. 단, TLS를 프록시에서 종료하는
     * 구조(ALB 등)로 가면 X-Forwarded-Proto를 반영하도록 server.forward-headers-strategy 설정이
     * 함께 필요하다.
     */
    private void addCookie(HttpServletRequest request, HttpServletResponse response,
                           String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        if (request.isSecure()) {
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
