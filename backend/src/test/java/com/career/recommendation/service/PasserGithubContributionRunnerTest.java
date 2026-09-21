package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.exception.GithubUserNotFoundException;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasserGithubContributionRunnerTest {

    @Mock
    private GithubClient githubClient;
    @Mock
    private PasserDataRepository passerDataRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PasserGithubContributionRunner runner;

    private void stubTransactionManager() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void 정상_분석_결과가_areas_stack_githubDerived에_저장된다() {
        UUID reportId = UUID.randomUUID();
        PasserData report = PasserData.builder().id(reportId).jobType("BACKEND").year(2026).build();

        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "repo", null, Map.of("Java", 100L),
                List.of("src/main/java/com/example/controller/A.java", "src/test/java/ATest.java"),
                List.of("spring-boot"),
                12, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1)));

        stubTransactionManager();
        when(githubClient.analyze(eq("octocat"), eq(LocalDate.of(2026, 12, 31))))
                .thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(passerDataRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(passerDataRepository.saveAndFlush(any(PasserData.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(reportId, "octocat", 2026);

        ArgumentCaptor<PasserData> captor = ArgumentCaptor.forClass(PasserData.class);
        verify(passerDataRepository).saveAndFlush(captor.capture());
        PasserData saved = captor.getValue();

        assertThat(saved.getAreas()).contains("API", "TEST");
        assertThat(saved.getStack()).containsExactly("spring-boot");
        assertThat(saved.getGithubDerived())
                .containsEntry("repos", 1)
                .containsEntry("commits", 12)
                .containsKey("activeMonths")
                .containsKey("jobRatios");
    }

    @Test
    void 합격연도가_있으면_그해_12월31일_컷으로_analyze를_호출한다() {
        UUID reportId = UUID.randomUUID();
        when(githubClient.analyze(eq("octocat"), eq(LocalDate.of(2025, 12, 31))))
                .thenReturn(new GithubClient.GithubAnalysisRawResult(List.of(), false));

        runner.analyze(reportId, "octocat", 2025);

        verify(githubClient).analyze("octocat", LocalDate.of(2025, 12, 31));
    }

    @Test
    void 합격연도가_없으면_컷_없이_analyze를_호출한다() {
        UUID reportId = UUID.randomUUID();
        when(githubClient.analyze(eq("octocat"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new GithubClient.GithubAnalysisRawResult(List.of(), false));

        runner.analyze(reportId, "octocat", null);

        verify(githubClient).analyze("octocat", null);
    }

    @Test
    void 분석이_실패하면_조용히_아무것도_저장하지_않는다() {
        UUID reportId = UUID.randomUUID();
        when(githubClient.analyze(any(), any())).thenThrow(new GithubUserNotFoundException("octocat"));

        runner.analyze(reportId, "octocat", 2026);

        verify(passerDataRepository, never()).saveAndFlush(any());
        verify(passerDataRepository, never()).findById(any());
    }

    @Test
    void 레포가_없으면_아무것도_저장하지_않는다() {
        UUID reportId = UUID.randomUUID();
        when(githubClient.analyze(any(), any())).thenReturn(new GithubClient.GithubAnalysisRawResult(List.of(), false));

        runner.analyze(reportId, "octocat", 2026);

        verify(passerDataRepository, never()).saveAndFlush(any());
    }

    @Test
    void 제보가_이미_삭제됐으면_아무것도_하지_않는다() {
        UUID reportId = UUID.randomUUID();
        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "repo", null, Map.of("Java", 10L), List.of("a/service/X.java"), List.of(),
                5, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)));

        stubTransactionManager();
        when(githubClient.analyze(any(), any())).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(passerDataRepository.findById(reportId)).thenReturn(Optional.empty());

        runner.analyze(reportId, "octocat", 2026);

        verify(passerDataRepository, never()).saveAndFlush(any());
    }
}
