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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                requestBody.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile proofPart = new MockMultipartFile(
                "proof",
                "accepted.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        mockMvc.perform(multipart("/api/v1/passers/reports")
                        .file(requestPart)
                        .file(proofPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(passerReportService);
    }
}
