package com.career.recommendation.service;

import com.career.recommendation.dto.github.GithubConnectRequest;
import com.career.recommendation.dto.github.GithubConnectResponse;
import com.career.recommendation.dto.github.GithubProfileResponse;
import com.career.recommendation.domain.GithubProfileStatus;
import com.career.recommendation.entity.GithubProfile;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.GithubAnalysisCooldownException;
import com.career.recommendation.exception.GithubAnalysisInProgressException;
import com.career.recommendation.repository.GithubProfileRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.GithubUsernameParser;
import com.career.recommendation.util.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * E3(1단계) — GitHub 공개 레포 분석의 동기 진입점(컨트롤러가 직접 호출하는 부분).
 * 실제 GitHub API 호출·직무 분류·경험 파생은 {@link GithubAnalysisRunner}(@Async 전용 빈)가
 * 담당한다 — RecommendationCacheService와 같은 이유로 별도 빈으로 분리했다: 같은 빈 안에서
 * this.analyze(...) 형태로 자기 자신을 호출하면 Spring 프록시가 @Async(및 @Transactional)
 * 어드바이스를 가로채지 못해 동기적으로(또는 트랜잭션 없이) 실행돼버린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GithubAnalysisService {

    private final CurrentUserService currentUserService;
    private final GithubProfileRepository githubProfileRepository;
    private final TargetJobRepository targetJobRepository;
    private final UserSpecRepository userSpecRepository;
    private final PlatformTransactionManager transactionManager;
    private final GithubAnalysisRunner githubAnalysisRunner;

    private static final Duration REANALYSIS_COOLDOWN = Duration.ofHours(24);

    /**
     * PENDING인데 이만큼 지나면 "죽은 PENDING"으로 간주한다 — main 머지마다 EC2 자동 배포가
     * 서버를 재시작하는 이 프로젝트 특성상, @Async로 돌던 분석이 재시작으로 통째로 사라져도
     * DB에는 PENDING이 그대로 남는다. 이 값이 없으면 doUpsert가 영원히 409(진행 중)만 던져
     * 사용자가 다시는 분석을 요청할 수 없게 된다. 정상 분석(레포 최대 30개, 레포당 순차 호출
     * 4회 이하)은 몇 분 안에 끝나므로 15분은 정상 흐름을 절대 건드리지 않는 충분한 여유값이다.
     */
    private static final Duration STALE_PENDING_TIMEOUT = Duration.ofMinutes(15);

    /**
     * ⚠️ UserSpecService.saveOrUpdateMySpec과 같은 이유로 NOT_SUPPORTED가 필요하다 — 클래스
     * 레벨 readOnly 트랜잭션이 살아있으면 upsert()의 REQUIRES_NEW 재시도가 그 트랜잭션에
     * 합류해 조용히 아무 SQL도 안 보낼 수 있다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GithubConnectResponse connect(Authentication authentication, GithubConnectRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        String username = GithubUsernameParser.parse(request.getUrl());

        GithubProfile profile = upsertForNewRequest(user, username);
        try {
            githubAnalysisRunner.analyze(profile.getId(), user.getId());
        } catch (RejectedExecutionException e) {
            // TaskRejectedException(Spring의 TaskExecutor 거부 예외)은 RejectedExecutionException의
            // 하위 타입이라 이 한 catch로 둘 다 잡힌다 — Executor 구현체가 무엇이든(Spring
            // ThreadPoolTaskExecutor든 java.util.concurrent 것이든) 방어된다.
            // 실행기 큐가 가득 차서 작업 자체를 받아들이지 못한 경우 — PENDING으로 방치하면
            // 이 요청도 "죽은 PENDING"이 되어 다음 요청이 15분간 stale 판정을 기다려야 한다.
            // 이 실패는 같은 요청 안에서 동기적으로 이미 확정됐으므로, 응답 바디도 즉시 FAILED를
            // 정직하게 반영한다(막 접수한 요청을 PENDING이라고 돌려주는 건 거짓이다) — HTTP
            // 상태 코드 자체는 202를 유지해 이 호출 자체를 실패로 만들지 않는다.
            log.warn("GitHub 분석 작업 등록 실패 (실행기 큐 포화): profileId={}", profile.getId(), e);
            markAnalysisStartFailed(profile);
        }
        return GithubConnectResponse.of(profile.getUsername(), profile.getStatus());
    }

    /**
     * connect()에서 @Async 작업 등록 자체가 거부됐을 때 프로필을 FAILED로 되돌린다. 방금 이
     * 요청 안에서 만들어지거나 갱신된 profile을 그대로 다시 저장한다 — 동시에 이 행을 건드릴
     * 다른 쓰기가 있을 수 없는 시점이라(비동기 작업이 아직 시작조차 못 했다) findById로 다시
     * 조회할 필요 없이 saveAndFlush(merge)로 충분하다.
     */
    private void markAnalysisStartFailed(GithubProfile profile) {
        profile.setStatus(GithubProfileStatus.FAILED.name());
        profile.setFailureReason("요청이 몰려 분석을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> githubProfileRepository.saveAndFlush(profile));
    }

    public GithubProfileResponse getMyProfile(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return githubProfileRepository.findByUser_Id(user.getId())
                .map(profile -> {
                    String targetJobCode = targetJobRepository.findByUser_Id(user.getId())
                            .map(TargetJob::getJobType)
                            .orElse(null);
                    return GithubProfileResponse.from(profile, targetJobCode);
                })
                .orElseGet(GithubProfileResponse::notConnected);
    }

    /** github_profiles 행 삭제 + source=GITHUB 파생 경험 제거(수동 항목 보존). */
    @Transactional
    public void disconnect(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        githubProfileRepository.deleteByUser_Id(user.getId());

        userSpecRepository.findByUser_Id(user.getId()).ifPresent(spec -> {
            List<Map<String, Object>> existing = spec.getExperiences();
            if (existing == null || existing.isEmpty()) {
                return;
            }
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> exp : existing) {
                if (!isGithubSourced(exp)) {
                    kept.add(exp);
                }
            }
            if (kept.size() != existing.size()) {
                spec.setExperiences(kept); // 관리 상태 엔티티 — dirty checking으로 커밋 시 반영
            }
        });
    }

    static boolean isGithubSourced(Map<String, Object> experience) {
        Object source = experience.get("source");
        return source != null && "GITHUB".equalsIgnoreCase(String.valueOf(source));
    }

    /**
     * UserSpecService.upsert()와 같은 구조 — user_id UNIQUE라 최초 요청 경합에 취약해
     * REQUIRES_NEW 삽입 시도 실패 시 새 트랜잭션에서 재조회 후 갱신한다. 동시 진행 중(PENDING)
     * 이거나 쿨다운(24h) 이내 재요청이면 이 트랜잭션 안에서 예외를 던져 롤백시킨다.
     */
    private GithubProfile upsertForNewRequest(User user, String username) {
        TransactionTemplate insertAttempt = new TransactionTemplate(transactionManager);
        insertAttempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return insertAttempt.execute(status -> doUpsert(user, username));
        } catch (DataIntegrityViolationException e) {
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
            retry.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return retry.execute(status -> doUpsert(user, username));
        }
    }

    private GithubProfile doUpsert(User user, String username) {
        LocalDateTime now = LocalDateTime.now(ServiceTime.ZONE_ID);
        GithubProfile profile = githubProfileRepository.findByUser_Id(user.getId()).orElse(null);

        if (profile == null) {
            GithubProfile created = GithubProfile.builder()
                    .user(user)
                    .username(username)
                    .status(GithubProfileStatus.PENDING.name())
                    .requestedAt(now)
                    .build();
            return githubProfileRepository.saveAndFlush(created);
        }

        if (GithubProfileStatus.PENDING.name().equals(profile.getStatus())) {
            boolean stale = profile.getRequestedAt() != null
                    && profile.getRequestedAt().isBefore(now.minus(STALE_PENDING_TIMEOUT));
            if (!stale) {
                throw new GithubAnalysisInProgressException("이미 GitHub 분석이 진행 중입니다.");
            }
            // 배포 재시작 등으로 @Async 작업이 사라졌는데 DB에는 PENDING만 남은 경우 — 새 요청으로
            // 접수한다. 쿨다운은 다시 검사하지 않는다 — 이 PENDING이 만들어질 때 이미 통과했고,
            // analyzedAt은 DONE 성공 시에만 갱신되므로(PENDING 전환은 건드리지 않음) 그 이후로
            // 더 최근 값이 될 수 없다.
        } else if (profile.getAnalyzedAt() != null && profile.getAnalyzedAt().isAfter(now.minus(REANALYSIS_COOLDOWN))) {
            throw new GithubAnalysisCooldownException("재분석은 마지막 분석 후 24시간이 지나야 요청할 수 있습니다.");
        }

        // 기존 결과(job_ratios·repos·commit_total·active_months·analyzed_at)는 분석이 실제로
        // 성공(DONE)할 때만 GithubAnalysisRunner가 교체한다 — 여기서는 새 요청 접수만 반영한다.
        profile.setUsername(username);
        profile.setStatus(GithubProfileStatus.PENDING.name());
        profile.setFailureReason(null);
        profile.setRequestedAt(now);
        return githubProfileRepository.saveAndFlush(profile);
    }
}
