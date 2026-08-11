package com.career.recommendation.controller;

import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController(/refresh, /logout)의 HTTP 계약 검증.
 *
 * 이 컨트롤러는 PUBLIC_URLS(/api/v1/auth/**)라 미인증 호출이 가능하므로,
 * (1) TokenService가 던지는 InvalidTokenException이 500이 아니라 401로 나가는지,
 * (2) 잘못된 요청 바디(빈 문자열·필드 누락·깨진 JSON)가 서비스까지 내려가지 않고
 *     400으로 끊기는지, 응답에 내부 예외 메시지가 실리지 않는지를 확인한다.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 리프레시_토큰이_유효하지_않으면_401을_반환한다() throws Exception {
        // 액세스 토큰을 refresh로 들고 오는 경우(typ 클레임 검증)도 이 경로로 떨어진다.
        when(tokenService.reissue(anyString()))
                .thenThrow(new InvalidTokenException("리프레시 토큰이 유효하지 않습니다."));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"an.access.token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("리프레시 토큰이 유효하지 않습니다."));
    }

    @Test
    void 리프레시_토큰이_빈_문자열이면_400이고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void 리프레시_토큰_필드가_없으면_400이고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void 로그아웃은_204를_반환하고_토큰을_폐기한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some.refresh.token\"}"))
                .andExpect(status().isNoContent());

        verify(tokenService).revoke("some.refresh.token");
    }

    @Test
    void 로그아웃_바디가_비어있으면_400이고_폐기를_시도하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(tokenService, never()).revoke(any());
    }

    @Test
    void 깨진_JSON_바디는_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(tokenService);
    }

    @Test
    void 바디가_아예_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tokenService);
    }
}
