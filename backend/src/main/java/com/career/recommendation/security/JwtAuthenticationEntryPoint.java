package com.career.recommendation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 함수 이름: commence()
     * 기능: 인증되지 않은 요청이 보호된 API에 접근했을 때 401 상태와
     *       {code: UNAUTHORIZED, message, timestamp} JSON 본문을 UTF-8로 응답한다.
     * 파라미터: HttpServletRequest request -> 인증 실패한 요청
     *           HttpServletResponse response -> 401 JSON을 쓸 응답
     *           AuthenticationException authException -> 인증 실패 사유 예외(본문에는 노출하지 않음)
     * 반환값: 없음
     * 예외: IOException -> 응답 본문 쓰기에 실패했을 때
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "code", "UNAUTHORIZED",
                "message", "인증이 필요합니다.",
                "timestamp", LocalDateTime.now().toString()
        )));
    }
}
