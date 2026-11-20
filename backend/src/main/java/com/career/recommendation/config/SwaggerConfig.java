package com.career.recommendation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * 함수 이름: openAPI()
     * 기능: Swagger/OpenAPI 문서 메타데이터 빈을 만든다. API 제목(Spec Road API)·설명(인증 방법
     *       안내)·버전(v1.0.0)과 HTTP Bearer(JWT) 보안 스키마 "bearerAuth"를 등록한다.
     *       전역 security 대신 인증이 필요한 컨트롤러에만 @SecurityRequirement를 붙인다
     *       (전역으로 걸면 공개 API에도 자물쇠가 떠서 혼란).
     * 파라미터: 없음
     * 반환값: 구성된 OpenAPI 객체
     */
    @Bean
    public OpenAPI openAPI() {
        // 전역 security 대신 인증이 필요한 컨트롤러에만 @SecurityRequirement를 붙인다
        // (전역으로 걸면 공개 API에도 자물쇠가 떠서 혼란)
        return new OpenAPI()
                .info(new Info()
                        .title("Spec Road API")
                        .description("""
                                AI 기반 커리어 추천 서비스 REST API

                                **인증 방법**
                                1. 브라우저에서 `GET /oauth2/authorization/kakao`로 이동해 카카오 로그인
                                2. 로그인 성공 시 프론트 콜백 URL로 accessToken이 전달됨
                                3. 우측 상단 Authorize 버튼에 accessToken 입력 후 🔒 표시된 API 호출
                                """)
                        .version("v1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
