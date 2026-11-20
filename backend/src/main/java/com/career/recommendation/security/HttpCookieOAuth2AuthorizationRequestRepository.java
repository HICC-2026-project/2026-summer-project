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

    /**
     * 함수 이름: createObjectMapper()
     * 기능: authorization request를 JSON으로 직렬화/역직렬화할 ObjectMapper를 만든다.
     *       JDK 직렬화 대신 Spring Security의 Jackson 모듈과 OAuth2 클라이언트 모듈을 등록해,
     *       위조된 쿠키 값으로 인한 역직렬화 RCE를 피한다.
     * 파라미터: 없음
     * 반환값: 보안 모듈이 등록된 ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader classLoader = HttpCookieOAuth2AuthorizationRequestRepository.class.getClassLoader();
        mapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        mapper.registerModule(new OAuth2ClientJackson2Module());
        return mapper;
    }

    /**
     * 함수 이름: loadAuthorizationRequest()
     * 기능: 요청의 oauth2_auth_request 쿠키를 찾아 OAuth2AuthorizationRequest로 역직렬화한다.
     *       쿠키가 없거나 값이 깨졌거나 위조되어 파싱에 실패하면 null을 돌려준다(삭제는 하지 않음).
     * 파라미터: HttpServletRequest request -> 카카오 콜백 요청
     * 반환값: 복원된 OAuth2AuthorizationRequest, 없거나 실패 시 null
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    /**
     * 함수 이름: saveAuthorizationRequest()
     * 기능: authorization request를 JSON+Base64로 직렬화해 180초짜리 HttpOnly 쿠키에 저장한다.
     *       null이 넘어오면 저장 대신 해당 쿠키를 삭제한다.
     * 파라미터: OAuth2AuthorizationRequest authorizationRequest -> 저장할 요청(null이면 쿠키 삭제)
     *           HttpServletRequest request -> 현재 요청(HTTPS 여부 판단용)
     *           HttpServletResponse response -> 쿠키를 추가할 응답
     * 반환값: 없음
     */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }
        addCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);
    }

    /**
     * 함수 이름: removeAuthorizationRequest()
     * 기능: 쿠키의 authorization request를 읽어 돌려주고, 일회용으로 쓰도록 쿠키를 즉시 삭제한다.
     * 파라미터: HttpServletRequest request -> 카카오 콜백 요청
     *           HttpServletResponse response -> 삭제 쿠키를 추가할 응답
     * 반환값: 복원된 OAuth2AuthorizationRequest, 없거나 실패 시 null
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return authorizationRequest;
    }

    /**
     * 함수 이름: getCookie()
     * 기능: 요청 쿠키 중 이름이 일치하는 첫 번째 쿠키를 찾는다. 쿠키가 하나도 없으면 빈 Optional.
     * 파라미터: HttpServletRequest request -> 쿠키를 읽을 요청
     *           String name -> 찾을 쿠키 이름
     * 반환값: 일치하는 Cookie를 담은 Optional (없으면 Optional.empty())
     */
    private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(name))
                .findFirst();
    }

    /**
     * 함수 이름: addCookie()
     * 기능: Path=/, HttpOnly, SameSite=Lax 속성의 쿠키를 응답에 추가한다. 현재 요청이 HTTPS
     *       (request.isSecure())일 때만 Secure를 켠다.
     *       SameSite=Lax는 항상 붙인다 — 카카오→백엔드 콜백은 top-level GET 리다이렉트라 Lax에서도
     *       정상 왕복된다.
     *
     *       ⚠️ Secure는 "프로파일이 local이 아니면 켠다"로 판단하면 안 된다. 실제로 그렇게 배포했다가
     * 프로덕션 카카오 로그인이 전면 불능이 된 적이 있다(2026-08-11): prod 프로파일이지만 서버가
     * 아직 평문 HTTP(탄력적 IP 직접 접근, 도메인·인증서 없음)라서, 브라우저가 Secure 쿠키를
     * 조용히 버렸다 → 카카오에서 돌아온 콜백에 state 쿠키가 없음 → authorization request 조회
     * 실패 → 전원 login_failed. 프로파일은 접속 프로토콜을 보장하지 않으므로, 지금 이 요청이
     * 실제로 HTTPS로 왔는지(request.isSecure())를 기준으로 켠다 — HTTP 요청에 Secure를 붙이는
     * 것은 어차피 브라우저가 저장을 거부하니 보호 효과도 없다. 나중에 HTTPS(도메인+인증서 또는
     * ALB)로 전환하면 별도 수정 없이 Secure가 자동으로 켜진다. 단, TLS를 프록시에서 종료하는
     * 구조(ALB 등)로 가면 X-Forwarded-Proto를 반영하도록 server.forward-headers-strategy 설정이
     * 함께 필요하다.
     * 파라미터: HttpServletRequest request -> 현재 요청(HTTPS 여부 판단용)
     *           HttpServletResponse response -> 쿠키를 추가할 응답
     *           String name -> 쿠키 이름
     *           String value -> 쿠키 값(직렬화된 authorization request)
     *           int maxAgeSeconds -> 쿠키 유효 시간(초)
     * 반환값: 없음
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

    /**
     * 함수 이름: deleteCookie()
     * 기능: 같은 이름의 쿠키를 빈 값, maxAge=0, Path=/ 로 응답에 추가해 브라우저가 삭제하게 한다.
     * 파라미터: HttpServletResponse response -> 삭제 쿠키를 추가할 응답
     *           String name -> 삭제할 쿠키 이름
     * 반환값: 없음
     */
    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 함수 이름: serialize()
     * 기능: OAuth2AuthorizationRequest를 Jackson으로 JSON 직렬화한 뒤 URL-safe Base64 문자열로 인코딩한다.
     * 파라미터: OAuth2AuthorizationRequest authorizationRequest -> 직렬화할 요청
     * 반환값: Base64(URL-safe) 인코딩된 JSON 문자열
     * 예외: IllegalStateException -> 직렬화에 실패했을 때
     */
    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(authorizationRequest);
            return Base64.getUrlEncoder().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest 직렬화에 실패했습니다.", e);
        }
    }

    /**
     * 함수 이름: deserialize()
     * 기능: 쿠키 값을 Base64 디코딩하고 JSON을 OAuth2AuthorizationRequest로 복원한다.
     *       값이 깨졌거나 위조되어 어떤 예외든 발생하면 조용히 null을 돌려준다.
     * 파라미터: Cookie cookie -> oauth2_auth_request 쿠키
     * 반환값: 복원된 OAuth2AuthorizationRequest, 실패 시 null
     */
    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cookie.getValue());
            return objectMapper.readValue(new String(json, StandardCharsets.UTF_8), OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            return null;
        }
    }
}
