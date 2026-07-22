package com.career.recommendation.controller;

import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.TargetJobService;
import com.career.recommendation.service.UserService;
import com.career.recommendation.service.UserSpecService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserSpecService userSpecService;

    @MockBean
    private TargetJobService targetJobService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void TOEIC_점수가_990점을_초과하면_400을_반환한다() throws Exception {
        String requestBody = """
                {
                  "gpa": 3.8,
                  "gpaMax": 4.5,
                  "grade": 3,
                  "languageScores": [
                    {
                      "type": "TOEIC",
                      "score": 1000,
                      "maxScore": 990
                    }
                  ],
                  "certifications": []
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/spec")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userSpecService);
    }

    @Test
    void 필수값이_누락되면_400을_반환한다() throws Exception {
        String requestBody = """
                {
                  "languageScores": [],
                  "certifications": []
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/spec")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userSpecService);
    }

    @Test
    void 중복된_어학시험이_있으면_400을_반환한다() throws Exception {
        String requestBody = """
                {
                  "gpa": 3.8,
                  "gpaMax": 4.5,
                  "grade": 3,
                  "languageScores": [
                    {
                      "type": "TOEIC",
                      "score": 850,
                      "maxScore": 990
                    },
                    {
                      "type": "TOEIC",
                      "score": 900,
                      "maxScore": 990
                    }
                  ],
                  "certifications": []
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/spec")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userSpecService);
    }
}
