package com.career.recommendation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 함수 이름: handle()
     * 기능: 인증은 됐지만 권한이 부족한 요청(예: 일반 사용자의 관리자 API 호출)에 대해 403 상태와
     *       {code: ACCESS_DENIED, message, timestamp} JSON 본문을 UTF-8로 응답한다.
     * 파라미터: HttpServletRequest request -> 거부된 요청
     *           HttpServletResponse response -> 403 JSON을 쓸 응답
     *           AccessDeniedException accessDeniedException -> 접근 거부 사유 예외(본문에는 노출하지 않음)
     * 반환값: 없음
     * 예외: IOException -> 응답 본문 쓰기에 실패했을 때
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "code", "ACCESS_DENIED",
                "message", "접근 권한이 없습니다.",
                "timestamp", LocalDateTime.now().toString()
        )));
    }
}
