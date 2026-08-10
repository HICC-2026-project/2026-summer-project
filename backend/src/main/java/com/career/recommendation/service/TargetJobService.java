package com.career.recommendation.service;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.TargetJobResponse;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.TargetJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetJobService {

    private final CurrentUserService currentUserService;
    private final TargetJobRepository targetJobRepository;
    private final PlatformTransactionManager transactionManager;

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
                targetJob.setJobType(request.getJobType());
                targetJob.setCompanySize(request.getCompanySize());
                targetJob.setIndustry(request.getIndustry());
                return targetJobRepository.saveAndFlush(targetJob);
            });
        } catch (DataIntegrityViolationException e) {
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
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
}