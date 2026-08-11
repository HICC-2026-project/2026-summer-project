package com.career.recommendation.service;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.TargetJobResponse;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.TargetJobRepository;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetJobService {

    private final CurrentUserService currentUserService;
    private final TargetJobRepository targetJobRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * ⚠️ UserSpecService.saveOrUpdateMySpec과 같은 이유로 NOT_SUPPORTED가 필요하다. 클래스
     * 레벨 @Transactional(readOnly = true)를 그대로 두면, upsert()의 insertAttempt
     * (REQUIRES_NEW)가 실패한 뒤 재개되는 이 메서드의 readOnly 트랜잭션에 retry가 합류해
     * saveAndFlush()가 예외 없이 조용히 아무 SQL도 안 보낸다 — 클라이언트는 200을 받지만
     * DB에는 반영되지 않는, 500보다 나쁜 조용한 데이터 유실이었다(Sonnet 5 재검토가
     * pg_backend_pid()로 직접 재현). NOT_SUPPORTED로 이 메서드를 트랜잭션 밖에 두면 매 호출이
     * 커넥션 풀에서 2개를 동시에 점유하던 문제(동시 요청 10개 이상에서 HikariCP 풀 고갈
     * 실측)도 같이 해소된다 — insertAttempt와 retry가 순차적으로만 열린다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TargetJobResponse saveOrUpdateMyTarget(
            Authentication authentication,
            TargetJobRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        TargetJob savedTargetJob = upsert(user, request);
        return TargetJobResponse.from(savedTargetJob);
    }

    /**
     * UserSpecService.upsert()와 같은 이유·같은 구조. target_jobs.user_id도 UNIQUE라 최초
     * 저장 시 같은 경합이 있었다(실제 동시 스레드 테스트로 100% 재현 확인). REQUIRES_NEW로
     * 격리된 삽입 시도가 실패하면(동시 요청이 먼저 INSERT함) 완전히 새 트랜잭션에서
     * 재조회 후 업데이트로 재시도한다 — PostgreSQL은 실패한 문장이 있던 트랜잭션 전체를
     * aborted 상태로 만들어 같은 트랜잭션 안에서 재시도하면 또 실패하기 때문이다.
     */
    private TargetJob upsert(User user, TargetJobRequest request) {
        TransactionTemplate insertAttempt = new TransactionTemplate(transactionManager);
        insertAttempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return insertAttempt.execute(status -> {
                TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId())
                        .orElseGet(() -> TargetJob.builder().user(user).build());
                // 기존 엔티티가 있고 값이 동일하면 save를 건너뛴다.
                // @UpdateTimestamp가 무변경 저장에도 updatedAt을 갱신하여
                // 추천 갱신 횟수가 헛되이 차감되는 것을 방지한다.
                if (targetJob.getId() != null && !hasChanges(targetJob, request)) {
                    return targetJob;
                }
                targetJob.setJobType(request.getJobType());
                targetJob.setCompanySize(request.getCompanySize());
                targetJob.setIndustry(request.getIndustry());
                return targetJobRepository.saveAndFlush(targetJob);
            });
        } catch (DataIntegrityViolationException e) {
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
            retry.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return retry.execute(status -> {
                TargetJob existing = targetJobRepository.findByUser_Id(user.getId())
                        .orElseThrow(() -> e);
                existing.setJobType(request.getJobType());
                existing.setCompanySize(request.getCompanySize());
                existing.setIndustry(request.getIndustry());
                return targetJobRepository.saveAndFlush(existing);
            });
        }
    }

    /**
     * 요청 값이 기존 엔티티와 동일한지 비교한다.
     * 동일하면 saveAndFlush()를 건너뛰어 @UpdateTimestamp가 updatedAt을 갱신하지 않게 한다.
     */
    private boolean hasChanges(TargetJob existing, TargetJobRequest request) {
        if (!Objects.equals(existing.getJobType(), request.getJobType())) return true;
        if (!Objects.equals(existing.getCompanySize(), request.getCompanySize())) return true;
        if (!Objects.equals(existing.getIndustry(), request.getIndustry())) return true;
        return false;
    }
}