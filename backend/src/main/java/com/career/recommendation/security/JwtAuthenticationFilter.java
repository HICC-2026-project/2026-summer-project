package com.career.recommendation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 함수 이름: doFilterInternal()
     * 기능: Authorization 헤더의 Bearer 토큰을 꺼내 서명·만료가 유효하고 typ 클레임이 access인
     *       경우에만 SecurityContext에 인증 정보(사용자 UUID, ROLE_ + role 권한)를 설정한다.
     *       리프레시 토큰은 서명·만료 구조가 액세스 토큰과 같아서, isAccessToken()으로 명시적
     *       typ 클레임을 확인하지 않으면 리프레시 토큰도 그대로 API 인증에 통용된다(로그아웃해도
     *       만료 전까지 계속 쓸 수 있게 됨 — JwtTokenProvider의 CLAIM_TYPE 주석 참고).
     *       토큰이 없거나 유효하지 않으면 인증 없이 다음 필터로 넘긴다(거부는 이후 인가 단계에서 처리).
     * 파라미터: HttpServletRequest request -> 들어온 요청
     *           HttpServletResponse response -> 응답
     *           FilterChain filterChain -> 다음 필터로 넘기기 위한 체인
     * 반환값: 없음
     * 예외: ServletException, IOException -> 필터 체인 실행 중 발생한 예외
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        // 리프레시 토큰은 서명·만료 구조가 액세스 토큰과 같아서, isAccessToken()으로 명시적
        // typ 클레임을 확인하지 않으면 리프레시 토큰도 그대로 API 인증에 통용된다(로그아웃해도
        // 만료 전까지 계속 쓸 수 있게 됨 — JwtTokenProvider의 CLAIM_TYPE 주석 참고).
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 함수 이름: resolveToken()
     * 기능: Authorization 헤더가 "Bearer "로 시작하면 접두어를 제거한 토큰 문자열을 꺼낸다.
     * 파라미터: HttpServletRequest request -> 헤더를 읽을 요청
     * 반환값: 토큰 문자열, 헤더가 없거나 Bearer 형식이 아니면 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
