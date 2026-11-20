package com.career.recommendation.config;

import com.career.recommendation.entity.User;
import com.career.recommendation.security.CustomOAuth2UserService;
import com.career.recommendation.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.career.recommendation.security.JwtAccessDeniedHandler;
import com.career.recommendation.security.JwtAuthenticationEntryPoint;
import com.career.recommendation.security.JwtAuthenticationFilter;
import com.career.recommendation.security.OAuth2LoginFailureHandler;
import com.career.recommendation.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private static final String[] PUBLIC_URLS = {
            "/api/v1/auth/**",
            "/api/v1/activities/**",
            "/api/v1/jobs/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/swagger-ui.html",   // 진입 URL — /swagger-ui/** 패턴에 안 걸리므로 별도 등록 필요
            "/swagger-ui/**",
            "/api-docs/**",
            "/actuator/health",   // 배포 헬스체크(backend-deploy.yml)
            "/error"
    };

    /**
     * 함수 이름: filterChain()
     * 기능: 보안 필터 체인 빈을 구성한다. CSRF 비활성화, CORS 설정 적용, 세션 STATELESS를 설정한다.
     *       인가 규칙은 PUBLIC_URLS는 모두 허용, /api/v1/admin/** 는 ADMIN 역할만, 그 외 모든 요청은
     *       인증 필요이다. 인증 실패는 401(JwtAuthenticationEntryPoint), 권한 부족은 403
     *       (JwtAccessDeniedHandler)으로 응답한다. 카카오 OAuth2 로그인은 쿠키 기반 authorization
     *       request 저장소, 커스텀 유저 서비스, 성공/실패 핸들러를 연결하고, JwtAuthenticationFilter를
     *       UsernamePasswordAuthenticationFilter 앞에 추가한다.
     * 파라미터: HttpSecurity http -> 스프링이 주입하는 보안 설정 빌더
     * 반환값: 구성된 SecurityFilterChain
     * 예외: Exception -> HttpSecurity 구성 중 오류가 발생했을 때
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(User.ROLE_ADMIN)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(authorizationRequestRepository)
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 함수 이름: corsConfigurationSource()
     * 기능: 모든 경로(/**)에 적용되는 CORS 설정 빈을 만든다. 허용 출처는 app.cors.allowed-origins
     *       설정값(패턴), 허용 메서드는 GET/POST/PUT/DELETE/OPTIONS, 허용 헤더는 전체이며,
     *       자격 증명(쿠키·인증 헤더) 전송을 허용한다.
     * 파라미터: 없음
     * 반환값: CORS 설정이 등록된 CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
