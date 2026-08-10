package com.career.recommendation.service;

import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSpecService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final PlatformTransactionManager transactionManager;

    public UserSpecResponse saveOrUpdateMySpec(
            Authentication authentication,
            UserSpecRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        UserSpec savedUserSpec = upsert(user, request);
        return UserSpecResponse.from(savedUserSpec);
    }

    /**
     * find-then-save는 최초 저장 시 경합에 취약하다. user_specs.user_id는 UNIQUE라, 신규 유저의
     * 저장 요청 두 개가 거의 동시에 findByUser_Id에서 둘 다 "없음"을 보면 둘 다 INSERT를
     * 시도하고 늦은 쪽이 DataIntegrityViolationException으로 500이 났다(실제 동시 스레드
     * 테스트로 100% 재현 확인).
     *
     * 이 예외를 같은 트랜잭션 안에서 잡고 재조회해 재시도하면 안 된다 — PostgreSQL은 한 문장이
     * 실패하면 그 트랜잭션 전체를 "aborted" 상태로 만들어, 같은 커넥션으로 이어지는 모든 문장이
     * "current transaction is aborted" 오류로 실패한다. 그래서 삽입 시도를 REQUIRES_NEW로
     * 격리된 트랜잭션에서 수행한다 — 실패해도 그 트랜잭션만 롤백되고, 이어지는 재조회·재시도는
     * 완전히 새 트랜잭션에서 깨끗하게 실행된다. self-invocation(같은 빈 안에서 this.메서드()
     * 호출) 문제 없이 REQUIRES_NEW를 걸기 위해 프록시가 아니라 TransactionTemplate을 직접
     * 쓴다(RecommendationCacheService가 REQUIRES_NEW를 별도 빈으로 분리한 것과 같은 이유 —
     * 여기서는 별도 빈 대신 TransactionTemplate으로 같은 효과를 낸다).
     *
     * saveAndFlush를 쓰는 이유는 별개다 — save()만 쓰면 응답 DTO를 만들 때 @UpdateTimestamp인
     * updatedAt이 아직 flush 전이라 최초 저장 시 null, 수정 시 한 리비전 뒤처진 값으로 나간다.
     */
    private UserSpec upsert(User user, UserSpecRequest request) {
        TransactionTemplate insertAttempt = new TransactionTemplate(transactionManager);
        insertAttempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return insertAttempt.execute(status -> {
                UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId())
                        .orElseGet(() -> UserSpec.builder().user(user).build());
                updateUserSpecFields(userSpec, request);
                return userSpecRepository.saveAndFlush(userSpec);
            });
        } catch (DataIntegrityViolationException e) {
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
            return retry.execute(status -> {
                UserSpec existing = userSpecRepository.findByUser_Id(user.getId())
                        .orElseThrow(() -> e);
                updateUserSpecFields(existing, request);
                return userSpecRepository.saveAndFlush(existing);
            });
        }
    }

    private void updateUserSpecFields(
            UserSpec userSpec,
            UserSpecRequest request
    ) {
        userSpec.setGpa(request.getGpa());
        userSpec.setGpaMax(request.getGpaMax());
        userSpec.setGrade(request.getGrade());
        userSpec.setLanguageScores(convertLanguageScores(request));
        userSpec.setCertifications(convertCertifications(request));
    }

    private List<Map<String, Object>> convertLanguageScores(
            UserSpecRequest request
    ) {
        if (request.getLanguageScores() == null) {
            return Collections.emptyList();
        }

        return request.getLanguageScores().stream()
                .filter(Objects::nonNull)
                .map(LanguageScoreRequest::toMap)
                .collect(Collectors.toList());
    }

    private String[] convertCertifications(UserSpecRequest request) {
        if (request.getCertifications() == null) {
            return new String[0];
        }

        return request.getCertifications().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(certification -> !certification.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
