package com.career.recommendation.service;

import com.career.recommendation.domain.GithubProfileStatus;
import com.career.recommendation.entity.GithubProfile;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.exception.GithubUserNotFoundException;
import com.career.recommendation.repository.GithubProfileRepository;
import com.career.recommendation.repository.UserSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisRunnerTest {

    @Mock
    private GithubClient githubClient;
    @Mock
    private GithubProfileRepository githubProfileRepository;
    @Mock
    private UserSpecRepository userSpecRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private GithubAnalysisRunner runner;

    private void stubTransactionManager() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private GithubProfile pendingProfile(UUID profileId, User user) {
        return GithubProfile.builder()
                .id(profileId)
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.PENDING.name())
                .build();
    }

    private User user(UUID userId) {
        return User.builder().id(userId).provider("KAKAO").providerId("p").nickname("t").build();
    }

    @Test
    void 정상_완료시_상태와_직무비율이_저장되고_커밋상위_레포가_파생경험으로_병합된다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));

        // 서로 다른 커밋 수를 가진 6개 레포 — 상위 5개만 파생 경험이 되어야 한다.
        List<GithubClient.RepoRawData> repos = IntStream.range(0, 6)
                .mapToObj(i -> new GithubClient.RepoRawData(
                        "repo-" + i, null, Map.of("Java", 100L),
                        List.of("src/main/java/com/example/controller/A.java"),
                        List.of("spring-boot"),
                        10 * (i + 1), // 10,20,...,60 — repo-5가 최다 커밋
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)))
                .collect(Collectors.toList());

        UserSpec spec = UserSpec.builder()
                .id(UUID.randomUUID())
                .user(user(userId))
                .experiences(new ArrayList<>(List.of(
                        Map.of("type", "PROJECT", "title", "수동 프로젝트", "source", "MANUAL"),
                        Map.of("type", "PROJECT", "title", "옛날 GitHub 항목", "source", "GITHUB")
                )))
                .build();

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(spec));
        when(userSpecRepository.saveAndFlush(any(UserSpec.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        ArgumentCaptor<GithubProfile> profileCaptor = ArgumentCaptor.forClass(GithubProfile.class);
        verify(githubProfileRepository).saveAndFlush(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getStatus()).isEqualTo(GithubProfileStatus.DONE.name());
        assertThat(profileCaptor.getValue().getCommitTotal()).isEqualTo(10 + 20 + 30 + 40 + 50 + 60);
        assertThat(profileCaptor.getValue().getRepos()).hasSize(6);

        ArgumentCaptor<UserSpec> specCaptor = ArgumentCaptor.forClass(UserSpec.class);
        verify(userSpecRepository).saveAndFlush(specCaptor.capture());
        List<Map<String, Object>> savedExperiences = specCaptor.getValue().getExperiences();

        // 수동 1개 + GITHUB 파생 5개(상위 커밋순) = 6개. 옛 GITHUB 항목은 사라져야 한다.
        assertThat(savedExperiences).hasSize(6);
        assertThat(savedExperiences.get(0)).containsEntry("title", "수동 프로젝트");
        List<String> githubTitles = savedExperiences.stream()
                .filter(e -> "GITHUB".equals(e.get("source")))
                .map(e -> (String) e.get("title"))
                .toList();
        assertThat(githubTitles).containsExactly("repo-5", "repo-4", "repo-3", "repo-2", "repo-1");
        assertThat(githubTitles).doesNotContain("옛날 GitHub 항목", "repo-0");
    }

    @Test
    void 경험이_null이면_새_리스트로_파생경험만_채워진다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));
        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "solo-repo", null, Map.of("Java", 10L), List.of("a/service/X.java"), List.of(),
                5, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)));

        UserSpec spec = UserSpec.builder().id(UUID.randomUUID()).user(user(userId)).experiences(null).build();

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(spec));
        when(userSpecRepository.saveAndFlush(any(UserSpec.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        assertThat(spec.getExperiences()).isNotNull().hasSize(1);
        assertThat(spec.getExperiences().get(0)).containsEntry("title", "solo-repo").containsEntry("source", "GITHUB");
    }

    @Test
    void 파생_경험에_레포_신호로_months_stack_areas가_채워진다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));
        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "solo-repo", null, Map.of("Java", 10L), List.of("a/service/X.java"), List.of(),
                5, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)));

        UserSpec spec = UserSpec.builder().id(UUID.randomUUID()).user(user(userId)).experiences(null).build();

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(spec));
        when(userSpecRepository.saveAndFlush(any(UserSpec.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        Map<String, Object> derived = spec.getExperiences().get(0);
        // src/…/service/X.java 하나만으로도 activeMonths=1(같은 달), API 경로 신호, 의존성이
        // 없으니 stack은 주 언어(Java) 하나로 채워져야 한다.
        assertThat(derived).containsEntry("months", 1);
        assertThat(derived).containsEntry("stack", List.of("Java"));
        assertThat(derived).containsEntry("areas", List.of("API"));
    }

    @Test
    void 수동_항목이_20개면_GITHUB_파생을_추가하지_않는다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));
        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "repo", null, Map.of("Java", 10L), List.of("a/service/X.java"), List.of(),
                5, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)));

        List<Map<String, Object>> manualTwenty = IntStream.range(0, 20)
                .mapToObj(i -> (Map<String, Object>) new java.util.LinkedHashMap<String, Object>(
                        Map.of("type", "ETC", "title", "수동 " + i, "source", "MANUAL")))
                .collect(Collectors.toList());
        UserSpec spec = UserSpec.builder().id(UUID.randomUUID()).user(user(userId))
                .experiences(new ArrayList<>(manualTwenty)).build();

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, false));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(spec));
        when(userSpecRepository.saveAndFlush(any(UserSpec.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        assertThat(spec.getExperiences()).hasSize(20);
        assertThat(spec.getExperiences()).allMatch(e -> "MANUAL".equals(e.get("source")));
    }

    @Test
    void 계정을_찾을_수_없으면_FAILED로_저장하고_경험은_건드리지_않는다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenThrow(new GithubUserNotFoundException("octocat"));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        ArgumentCaptor<GithubProfile> captor = ArgumentCaptor.forClass(GithubProfile.class);
        verify(githubProfileRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GithubProfileStatus.FAILED.name());
        assertThat(captor.getValue().getFailureReason()).isEqualTo("존재하지 않는 계정입니다.");

        verify(userSpecRepository, never()).findByUser_Id(any());
    }

    @Test
    void 레이트리밋이면_부분결과를_저장하되_파생경험은_병합하지_않는다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GithubProfile profile = pendingProfile(profileId, user(userId));
        List<GithubClient.RepoRawData> repos = List.of(new GithubClient.RepoRawData(
                "partial-repo", null, Map.of("Java", 10L), List.of("a/service/X.java"), List.of(),
                5, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)));

        stubTransactionManager();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(githubClient.analyze("octocat")).thenReturn(new GithubClient.GithubAnalysisRawResult(repos, true));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.analyze(profileId, userId);

        ArgumentCaptor<GithubProfile> captor = ArgumentCaptor.forClass(GithubProfile.class);
        verify(githubProfileRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GithubProfileStatus.RATE_LIMITED.name());
        assertThat(captor.getValue().getRepos()).hasSize(1);

        verify(userSpecRepository, never()).findByUser_Id(any());
    }

    @Test
    void 프로필이_이미_삭제됐으면_아무것도_하지_않는다() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(githubProfileRepository.findById(profileId)).thenReturn(Optional.empty());

        runner.analyze(profileId, userId);

        verify(githubClient, never()).analyze(any());
        verify(githubProfileRepository, never()).saveAndFlush(any());
    }
}
