package com.career.recommendation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;

    /**
     * 함수 이름: onAuthenticationFailure()
     * 기능: OAuth2 로그인 실패 시 오류를 로그로 남기고, 상세 사유는 노출하지 않은 채 프론트 리다이렉트
     *       URI에 error=login_failed 쿼리 파라미터만 붙여 리다이렉트한다.
     * 파라미터: HttpServletRequest request -> 실패한 콜백 요청
     *           HttpServletResponse response -> 리다이렉트를 보낼 응답
     *           AuthenticationException exception -> 로그인 실패 사유 예외(로그에만 기록)
     * 반환값: 없음
     * 예외: IOException -> 리다이렉트 전송에 실패했을 때
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        log.error("OAuth2 로그인 실패", exception);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("error", "login_failed")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
