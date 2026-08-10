package com.career.recommendation.controller;

import com.career.recommendation.security.JwtTokenProvider;
import com.career.recommendation.service.PasserReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasserReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasserReportControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasserReportService passerReportService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 동의하지_않은_제보는_400을_반환한다() throws Exception {
        String requestBody = """
                {
                  "jobType": "BACKEND",
                  "year": 2026,
                  "gpa": 3.8,
                  "gpaMax": 4.5,
                  "languageScores": [],
                  "certifications": [],
                  "experienceCount": 2,
                  "consent": false
                }
                """;

        mockMvc.perform(post("/api/v1/passers/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(passerReportService);
    }
}
