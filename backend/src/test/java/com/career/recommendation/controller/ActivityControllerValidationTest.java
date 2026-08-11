package com.career.recommendation.controller;

import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler가 실제 요청 파이프라인(컨트롤러 파라미터 바인딩 → 예외 리졸버)을
 * 거칠 때 정확한 상태 코드로 응답하는지 검증한다.
 *
 * 예전엔 @ExceptionHandler(RuntimeException.class)가 폭넓게 걸려 있어, UUID/enum 파싱
 * 실패나 page·size 검증 실패가 전부 500(INTERNAL_ERROR)으로 나갔다 — 그것도 인증이 필요
 * 없는(PUBLIC_URLS) 이 엔드포인트에서, e.getMessage()로 내부 예외 메시지까지 그대로
 * 노출한 채였다. CurrentUserService가 던지는 401/404(ResponseStatusException)도 같은
 * 이유로 500이 됐었다.
 */
@WebMvcTest(ActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 활동_ID가_UUID_형식이_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/activities/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        verifyNoInteractions(activityService);
    }

    @Test
    void 정렬_방향이_유효하지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/activities").param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void 정렬_방향_소문자도_정상_동작한다() throws Exception {
        // Sort.Direction을 컨트롤러 파라미터로 직접 바인딩하면(Enum.valueOf 기반) 대소문자를
        // 가려 흔한 소문자 입력(direction=desc)이 400이었다. String으로 받아
        // Sort.Direction.fromString()으로 변환하도록 고친 뒤 이 케이스가 실제로 통과하는지
        // 직접 검증한다 — 인증이 필요 없는 공개 API라 클라이언트 다양성이 크다.
        when(activityService.getActivities(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/activities").param("direction", "desc"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/activities").param("direction", "asc"))
                .andExpect(status().isOk());
    }

    @Test
    void 정렬_방향_기본값은_대문자_DESC로_정상_동작한다() throws Exception {
        when(activityService.getActivities(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/activities").param("direction", "DESC"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/activities").param("direction", "ASC"))
                .andExpect(status().isOk());
    }

    @Test
    void size가_상한을_넘으면_400을_반환하고_DB를_긁지_않는다() throws Exception {
        // 컨트롤러가 직접 ResponseStatusException(BAD_REQUEST, ...)을 던지는 경로라
        // GlobalExceptionHandler의 handleResponseStatusException을 타고 code는
        // HttpStatus 이름("BAD_REQUEST")이 된다(파라미터 타입 변환 실패 경로의
        // "INVALID_PARAMETER"와는 다른 핸들러).
        mockMvc.perform(get("/api/v1/activities").param("size", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(activityService);
    }

    @Test
    void size가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/activities").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/activities").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 화이트리스트에_없는_정렬_필드는_400을_반환하고_엔티티_구조를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/activities").param("sortBy", "아무필드나"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                // 화이트리스트 검증이 먼저 걸려 PropertyReferenceException까지 도달하지
                // 않으므로, 메시지에 Activity 엔티티의 실제 프로퍼티명이 노출되지 않는다.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PropertyPath"))));
    }

    @Test
    void ResponseStatusException은_지정된_상태코드_그대로_응답된다() throws Exception {
        // CurrentUserService가 실제로 던지는 것과 같은 예외를 서비스 레이어에서 흉내낸다.
        // 예전엔 이런 예외가 RuntimeException 핸들러에 잡혀 항상 500으로 나갔다.
        when(activityService.getActivity(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자가 존재하지 않습니다."));

        mockMvc.perform(get("/api/v1/activities/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자가 존재하지 않습니다."));
    }
}
