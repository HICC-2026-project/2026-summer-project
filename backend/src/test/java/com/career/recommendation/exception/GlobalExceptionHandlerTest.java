package com.career.recommendation.exception;

import com.career.recommendation.entity.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler의 분기 중 "실제 컨트롤러 경로로는 아무도 도달하지 못해" 한 번도
 * 실행 검증된 적이 없는 것들을 최소 스텁 컨트롤러로 직접 태운다.
 *
 * - MissingServletRequestParameterException: 현재 필수(@RequestParam required=true) 파라미터를
 *   쓰는 컨트롤러가 하나도 없어(ActivityController는 전부 defaultValue/required=false) 이 핸들러가
 *   실행된 적이 없다.
 * - PropertyReferenceException: ActivityController의 sortBy 화이트리스트가 앞에서 막아 도달 불가.
 * - IllegalArgumentException: PageRequest.of 검증도 컨트롤러의 명시적 검사에 선점돼 도달 불가.
 * - InvalidTokenException / ActivityNotFoundException: 도메인 예외 → 상태코드 매핑 고정.
 *
 * 여기서 검증하는 계약은 상태코드 + code 필드 + "내부 예외 메시지를 응답에 싣지 않는다"이다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class StubController {
        @GetMapping("/stub/required-param")
        String requiredParam(@RequestParam String q) {
            return q;
        }

        @GetMapping("/stub/property-reference")
        String propertyReference() {
            throw new PropertyReferenceException(
                    "존재하지않는필드", TypeInformation.of(Activity.class), Collections.emptyList());
        }

        @GetMapping("/stub/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("내부 상세: offset must not be less than zero");
        }

        @GetMapping("/stub/invalid-token")
        String invalidToken() {
            throw new InvalidTokenException("리프레시 토큰이 만료되었습니다.");
        }

        @GetMapping("/stub/activity-not-found")
        String activityNotFound() {
            throw new ActivityNotFoundException(UUID.randomUUID());
        }

        @GetMapping("/stub/boom")
        String boom() {
            throw new IllegalStateException("DB password=hunter2 in connection string");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 필수_쿼리_파라미터가_없으면_400_MISSING_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/stub/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("'q' 파라미터가 필요합니다."));
    }

    @Test
    void PropertyReferenceException은_400이고_엔티티_프로퍼티명을_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/stub/property-reference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("요청 파라미터 값이 올바르지 않습니다."));
    }

    @Test
    void IllegalArgumentException은_400이고_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/stub/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("요청 파라미터 값이 올바르지 않습니다."));
    }

    @Test
    void InvalidTokenException은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/stub/invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ActivityNotFoundException은_404를_반환한다() throws Exception {
        mockMvc.perform(get("/stub/activity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void 예상하지_못한_RuntimeException은_500이지만_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
