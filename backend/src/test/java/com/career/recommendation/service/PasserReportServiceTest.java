package com.career.recommendation.service;

import com.career.recommendation.dto.passer.PasserReportRequest;
import com.career.recommendation.dto.passer.PasserReportResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.DuplicatePasserReportException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    private PasserGithubContributionRunner passerGithubContributionRunner;

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
    void 같은_직무_연도로_검수_대기_중인_제보가_있으면_409이고_파일을_저장하지_않는다() {
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(passerDataRepository.existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull(user.getId(), "BACKEND", 2026))
                .thenReturn(true);

        assertThatThrownBy(() -> passerReportService.submit(authentication, validRequest(), proof))
                .isInstanceOf(DuplicatePasserReportException.class);

        // 거절될 요청의 증빙은 디스크에 닿지도 않아야 한다
        verify(localProofStorageService, never()).store(any());
        verify(passerDataRepository, never()).saveAndFlush(any());
    }

    @Test
    void 하루_5건을_넘기면_409() {
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(passerDataRepository.existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull(any(), any(), any()))
                .thenReturn(false);
        when(passerDataRepository.countByReporter_IdAndCreatedAtAfter(any(), any())).thenReturn(5L);

        assertThatThrownBy(() -> passerReportService.submit(authentication, validRequest(), proof))
                .isInstanceOf(DuplicatePasserReportException.class)
                .hasMessageContaining("5건");
        verify(localProofStorageService, never()).store(any());
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

    @Test
    void github_아이디와_동의가_있으면_저장_직후_비동기_분석을_트리거한다() {
        UUID reportId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        PasserReportRequest request = validRequest();
        request.setGithubUsername("octocat");
        request.setGithubConsent(true);
        request.setYear(2026);
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(localProofStorageService.store(proof)).thenReturn(
                new LocalProofStorageService.StoredProof("a.png", "stored.png", "image/png", 1L));
        when(passerDataRepository.saveAndFlush(any(PasserData.class))).thenAnswer(invocation -> {
            PasserData report = invocation.getArgument(0);
            report.setId(reportId);
            return report;
        });

        passerReportService.submit(authentication, request, proof);

        verify(passerGithubContributionRunner).analyze(reportId, "octocat", 2026);
    }

    @Test
    void github_아이디가_없으면_분석을_트리거하지_않는다() {
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        PasserReportRequest request = validRequest();
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(localProofStorageService.store(proof)).thenReturn(
                new LocalProofStorageService.StoredProof("a.png", "stored.png", "image/png", 1L));
        when(passerDataRepository.saveAndFlush(any(PasserData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passerReportService.submit(authentication, request, proof);

        verify(passerGithubContributionRunner, never()).analyze(any(), any(), any());
    }

    @Test
    void github_동의가_false면_아이디가_있어도_분석을_트리거하지_않는다() {
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        PasserReportRequest request = validRequest();
        request.setGithubUsername("octocat");
        request.setGithubConsent(false);
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(localProofStorageService.store(proof)).thenReturn(
                new LocalProofStorageService.StoredProof("a.png", "stored.png", "image/png", 1L));
        when(passerDataRepository.saveAndFlush(any(PasserData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passerReportService.submit(authentication, request, proof);

        verify(passerGithubContributionRunner, never()).analyze(any(), any(), any());
    }

    @Test
    void github_분석_시작이_실패해도_제보_자체는_성공한다() {
        UUID reportId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("p").build();
        PasserReportRequest request = validRequest();
        request.setGithubUsername("octocat");
        request.setGithubConsent(true);
        MockMultipartFile proof = new MockMultipartFile("proof", "a.png", "image/png", new byte[]{1});

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(localProofStorageService.store(proof)).thenReturn(
                new LocalProofStorageService.StoredProof("a.png", "stored.png", "image/png", 1L));
        when(passerDataRepository.saveAndFlush(any(PasserData.class))).thenAnswer(invocation -> {
            PasserData report = invocation.getArgument(0);
            report.setId(reportId);
            return report;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("실행기 큐 포화"))
                .when(passerGithubContributionRunner).analyze(any(), any(), any());

        PasserReportResponse response = passerReportService.submit(authentication, request, proof);

        assertThat(response.getReportId()).isEqualTo(reportId);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    /**
     * E11-5 요구사항 "아이디는 DB 컬럼 없음" — PasserData에 GitHub 아이디를 담을 getter/필드
     * 자체가 존재하지 않음을 리플렉션으로 못 박아둔다(존재한다면 어떻게 채워지든 저장·직렬화될
     * 잠재 경로가 생긴다).
     */
    @Test
    void PasserData_엔티티에_github_아이디를_담는_필드가_없다() {
        assertThatThrownBy(() -> PasserData.class.getDeclaredField("githubUsername"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> PasserData.class.getMethod("getGithubUsername"))
                .isInstanceOf(NoSuchMethodException.class);
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
