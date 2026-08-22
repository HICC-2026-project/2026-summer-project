package com.career.recommendation.service;

import com.career.recommendation.dto.passer.PasserReportRequest;
import com.career.recommendation.dto.passer.PasserReportResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasserReportServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private PasserDataRepository passerDataRepository;

    @Mock
    private LocalProofStorageService localProofStorageService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PasserReportService passerReportService;

    @Test
    void 제보는_미검수_USER_REPORT로_저장한다() {
        UUID reportId = UUID.randomUUID();
        User user = User.builder()
                .id(UUID.randomUUID())
                .provider("KAKAO")
                .providerId("provider-id")
                .build();
        PasserReportRequest request = validRequest();
        MockMultipartFile proof = new MockMultipartFile(
                "proof", "accepted.png", "image/png", new byte[]{1, 2, 3}
        );

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(localProofStorageService.store(proof)).thenReturn(
                new LocalProofStorageService.StoredProof(
                        "accepted.png", "proof-uuid.png", "image/png", 3L
                )
        );
        when(passerDataRepository.saveAndFlush(any(PasserData.class)))
                .thenAnswer(invocation -> {
                    PasserData report = invocation.getArgument(0);
                    report.setId(reportId);
                    return report;
                });

        PasserReportResponse response = passerReportService.submit(authentication, request, proof);

        ArgumentCaptor<PasserData> captor = ArgumentCaptor.forClass(PasserData.class);
        verify(passerDataRepository).saveAndFlush(captor.capture());

        PasserData saved = captor.getValue();
        assertThat(saved.getActivity()).isNull();
        assertThat(saved.getJobType()).isEqualTo("BACKEND");
        assertThat(saved.getReporter()).isSameAs(user);
        assertThat(saved.getGpa()).isEqualByComparingTo("3.8");
        assertThat(saved.getLanguageScores()).containsExactly(
                Map.of("type", "TOEIC", "score", 850, "maxScore", 990)
        );
        assertThat(saved.getCertifications()).containsExactly("정보처리기사", "SQLD");
        assertThat(saved.getIsVerified()).isFalse();
        assertThat(saved.getDataOrigin()).isEqualTo("USER_REPORT");
        assertThat(saved.getSpecSummary()).isNull();
        assertThat(saved.getProofOriginalName()).isEqualTo("accepted.png");
        assertThat(saved.getProofStoredName()).isEqualTo("proof-uuid.png");
        assertThat(saved.getProofContentType()).isEqualTo("image/png");
        assertThat(saved.getProofFileSize()).isEqualTo(3L);

        assertThat(response.getReportId()).isEqualTo(reportId);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 내_제보_목록은_검수_여부를_PENDING_VERIFIED로_돌려준다() {
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        PasserData pending = PasserData.builder().id(UUID.randomUUID()).jobType("BACKEND").year(2026)
                .isVerified(false).reporter(user).build();
        PasserData verified = PasserData.builder().id(UUID.randomUUID()).jobType("SECURITY").year(2025)
                .isVerified(true).reporter(user).build();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(passerDataRepository.findAllByReporter_IdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(pending, verified));

        var result = passerReportService.findMyReports(authentication);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.get(0).getJobTypeLabel()).isEqualTo("백엔드");
        assertThat(result.get(1).getStatus()).isEqualTo("VERIFIED");
        assertThat(result.get(1).getJobTypeLabel()).isEqualTo("보안");
    }

    private PasserReportRequest validRequest() {
        PasserReportRequest request = new PasserReportRequest();
        request.setJobType("backend");
        request.setYear(2026);
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setLanguageScores(List.of(toeic(850)));
        request.setCertifications(List.of(" 정보처리기사 ", "SQLD", "SQLD"));
        request.setExperienceCount(2);
        request.setConsent(true);
        return request;
    }

    private LanguageScoreRequest toeic(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("toeic");
        request.setScore(score);
        request.setMaxScore(990);
        return request;
    }
}
