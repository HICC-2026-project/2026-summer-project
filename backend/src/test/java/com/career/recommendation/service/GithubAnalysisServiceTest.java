package com.career.recommendation.service;

import com.career.recommendation.domain.GithubProfileStatus;
import com.career.recommendation.dto.github.GithubConnectRequest;
import com.career.recommendation.dto.github.GithubConnectResponse;
import com.career.recommendation.dto.github.GithubProfileResponse;
import com.career.recommendation.entity.GithubProfile;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.exception.GithubAnalysisCooldownException;
import com.career.recommendation.exception.GithubAnalysisInProgressException;
import com.career.recommendation.repository.GithubProfileRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private GithubProfileRepository githubProfileRepository;
    @Mock
    private TargetJobRepository targetJobRepository;
    @Mock
    private UserSpecRepository userSpecRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private GithubAnalysisRunner githubAnalysisRunner;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private GithubAnalysisService githubAnalysisService;

    private void stubTransactionManager() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void 최초_요청이면_PENDING_프로필을_만들고_비동기_분석을_트리거한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class)))
                .thenAnswer(inv -> {
                    GithubProfile p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("https://github.com/octocat");

        GithubConnectResponse response = githubAnalysisService.connect(authentication, request);

        assertThat(response.getUsername()).isEqualTo("octocat");
        assertThat(response.getStatus()).isEqualTo(GithubProfileStatus.PENDING.name());

        ArgumentCaptor<GithubProfile> captor = ArgumentCaptor.forClass(GithubProfile.class);
        verify(githubProfileRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("octocat");
        assertThat(captor.getValue().getStatus()).isEqualTo(GithubProfileStatus.PENDING.name());

        verify(githubAnalysisRunner).analyze(any(UUID.class), org.mockito.ArgumentMatchers.eq(userId));
    }

    @Test
    void 이미_PENDING이면_409_예외를_던지고_저장하지_않는다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile existing = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.PENDING.name())
                .requestedAt(LocalDateTime.now())
                .build();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existing));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");

        assertThatThrownBy(() -> githubAnalysisService.connect(authentication, request))
                .isInstanceOf(GithubAnalysisInProgressException.class);

        verify(githubProfileRepository, never()).saveAndFlush(any());
        verify(githubAnalysisRunner, never()).analyze(any(), any());
    }

    /**
     * 배포 재시작으로 @Async 작업이 사라져도 DB에는 PENDING이 남는 "죽은 PENDING" 시나리오.
     * requestedAt이 STALE_PENDING_TIMEOUT(15분)보다 오래됐으면 409 대신 새 요청으로 접수해야
     * 사용자가 영원히 재분석을 못 하는 상황을 막을 수 있다.
     */
    @Test
    void PENDING인데_16분전_요청이면_stale로_보고_새_요청으로_접수한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile existing = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.PENDING.name())
                .requestedAt(LocalDateTime.now().minusMinutes(16))
                .build();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existing));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");
        LocalDateTime beforeCall = LocalDateTime.now();

        GithubConnectResponse response = githubAnalysisService.connect(authentication, request);

        assertThat(response.getStatus()).isEqualTo(GithubProfileStatus.PENDING.name());
        assertThat(existing.getRequestedAt()).isAfterOrEqualTo(beforeCall.minusSeconds(5));
        verify(githubProfileRepository).saveAndFlush(existing);
        verify(githubAnalysisRunner).analyze(existing.getId(), userId);
    }

    @Test
    void PENDING이고_1분전_요청이면_여전히_409다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile existing = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.PENDING.name())
                .requestedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existing));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");

        assertThatThrownBy(() -> githubAnalysisService.connect(authentication, request))
                .isInstanceOf(GithubAnalysisInProgressException.class);

        verify(githubProfileRepository, never()).saveAndFlush(any());
        verify(githubAnalysisRunner, never()).analyze(any(), any());
    }

    @Test
    void 실행기_큐가_가득차_작업_등록이_거부되면_FAILED로_저장하고_202_응답은_그대로_준다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        UUID profileId = UUID.randomUUID();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class)))
                .thenAnswer(inv -> {
                    GithubProfile p = inv.getArgument(0);
                    if (p.getId() == null) p.setId(profileId);
                    return p;
                });
        doThrow(new TaskRejectedException("큐 포화"))
                .when(githubAnalysisRunner).analyze(any(UUID.class), any(UUID.class));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");

        GithubConnectResponse response = githubAnalysisService.connect(authentication, request);

        // 큐 포화는 같은 요청 안에서 동기적으로 이미 확정된 실패이므로 응답 바디도 정직하게
        // FAILED를 반영한다 — HTTP 상태 코드(202) 자체는 그대로 유지된다(컨트롤러 책임).
        assertThat(response.getStatus()).isEqualTo(GithubProfileStatus.FAILED.name());

        ArgumentCaptor<GithubProfile> captor = ArgumentCaptor.forClass(GithubProfile.class);
        verify(githubProfileRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        GithubProfile finalState = captor.getAllValues().get(1);
        assertThat(finalState.getStatus()).isEqualTo(GithubProfileStatus.FAILED.name());
        assertThat(finalState.getFailureReason())
                .isEqualTo("요청이 몰려 분석을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void 마지막_분석후_24시간_이내_재요청은_409_쿨다운_예외다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile existing = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.DONE.name())
                .analyzedAt(LocalDateTime.now().minusHours(1))
                .requestedAt(LocalDateTime.now().minusHours(1))
                .build();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existing));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");

        assertThatThrownBy(() -> githubAnalysisService.connect(authentication, request))
                .isInstanceOf(GithubAnalysisCooldownException.class);

        verify(githubProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 쿨다운_24시간이_지나면_재요청이_허용되어_PENDING으로_갱신된다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile existing = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.DONE.name())
                .analyzedAt(LocalDateTime.now().minusHours(25))
                .requestedAt(LocalDateTime.now().minusHours(25))
                .build();

        stubTransactionManager();
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existing));
        when(githubProfileRepository.saveAndFlush(any(GithubProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GithubConnectRequest request = new GithubConnectRequest();
        request.setUrl("octocat");

        GithubConnectResponse response = githubAnalysisService.connect(authentication, request);

        assertThat(response.getStatus()).isEqualTo(GithubProfileStatus.PENDING.name());
        assertThat(existing.getStatus()).isEqualTo(GithubProfileStatus.PENDING.name());
        verify(githubAnalysisRunner).analyze(existing.getId(), userId);
    }

    @Test
    void 미등록_사용자는_connected_false만_반환한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        GithubProfileResponse response = githubAnalysisService.getMyProfile(authentication);

        assertThat(response.isConnected()).isFalse();
        assertThat(response.getUsername()).isNull();
    }

    @Test
    void 목표_직무와_일치하는_jobRatio가_targetJobMatchRatio로_채워진다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        GithubProfile profile = GithubProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .username("octocat")
                .status(GithubProfileStatus.DONE.name())
                .jobRatios(List.of(
                        Map.of("jobType", "BACKEND", "ratio", 0.7),
                        Map.of("jobType", "FRONTEND", "ratio", 0.3)
                ))
                .repos(List.of())
                .commitTotal(10)
                .activeMonths(3)
                .build();
        TargetJob targetJob = TargetJob.builder().jobType("BACKEND").build();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(githubProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.of(targetJob));

        GithubProfileResponse response = githubAnalysisService.getMyProfile(authentication);

        assertThat(response.isConnected()).isTrue();
        assertThat(response.getTargetJobMatchRatio()).isEqualTo(0.7);
    }

    @Test
    void 연동_해제는_프로필을_삭제하고_수동_경험은_보존한채_GITHUB_파생만_제거한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        UserSpec spec = UserSpec.builder()
                .id(UUID.randomUUID())
                .user(user)
                .experiences(new java.util.ArrayList<>(List.of(
                        Map.of("type", "PROJECT", "title", "수동 항목", "source", "MANUAL"),
                        Map.of("type", "PROJECT", "title", "GitHub 항목", "source", "GITHUB")
                )))
                .build();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(spec));

        githubAnalysisService.disconnect(authentication);

        verify(githubProfileRepository).deleteByUser_Id(userId);
        assertThat(spec.getExperiences()).hasSize(1);
        assertThat(spec.getExperiences().get(0).get("title")).isEqualTo("수동 항목");
    }

    private User createUser(UUID userId) {
        return User.builder()
                .id(userId)
                .provider("KAKAO")
                .providerId("provider-id")
                .nickname("테스터")
                .build();
    }
}
