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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
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

    /**
     * ⚠️ 클래스 레벨 @Transactional(readOnly = true)를 명시적으로 벗어난다(NOT_SUPPORTED).
     * 이 메서드에 트랜잭션이 하나라도 걸려 있으면, 아래 upsert()의 insertAttempt(REQUIRES_NEW)가
     * 실패한 뒤 재개되는 시점에 이 메서드의 트랜잭션이 여전히 살아있는 채로 남아있고, retry가
     * REQUIRES_NEW 없이 PROPAGATION_REQUIRED로만 열리면 그 살아있는(=readOnly) 트랜잭션에
     * 합류해버린다 — Hibernate가 readOnly 세션에서 로드한 엔티티는 dirty-checking에서
     * 제외되므로, retry의 saveAndFlush()가 예외 없이 조용히 아무 SQL도 안 보낸다. 클라이언트는
     * 200과 함께 자신이 보낸 값을 그대로 돌려받지만 DB에는 전혀 반영되지 않는, 500보다 훨씬
     * 나쁜 조용한 데이터 유실이었다(Sonnet 5 재검토가 pg_backend_pid()로 직접 재현·확인).
     *
     * NOT_SUPPORTED로 이 메서드 자체를 트랜잭션 밖에 두면, upsert() 내부의 TransactionTemplate
     * 호출들이 각자 독립적으로 트랜잭션을 열고 닫는다 — insertAttempt와 retry는 같은 시점에
     * 동시에 열리지 않고 순차적으로만 열리므로, 이 메서드 하나가 커넥션 풀에서 항상 2개를
     * 동시에 점유하던 문제(동시 요청 10개 이상에서 HikariCP 풀 고갈 실측)도 같이 해소된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
                // 기존 엔티티가 있고 값이 동일하면 save를 건너뛴다.
                // @UpdateTimestamp가 무변경 저장에도 updatedAt을 갱신하여
                // 추천 갱신 횟수가 헛되이 차감되는 것을 방지한다.
                if (userSpec.getId() != null && !hasChanges(userSpec, request)) {
                    return userSpec;
                }
                updateUserSpecFields(userSpec, request);
                return userSpecRepository.saveAndFlush(userSpec);
            });
        } catch (DataIntegrityViolationException e) {
            // insertAttempt와 마찬가지로 REQUIRES_NEW를 명시한다 — saveOrUpdateMySpec을
            // NOT_SUPPORTED로 막아둔 지금은 합류할 활성 트랜잭션이 없어 필수는 아니지만,
            // "재시도는 항상 격리된 새 트랜잭션에서 실행한다"는 불변식을 REQUIRES_NEW
            // 하나로 명시적으로 못박아 둔다 — 이 메서드의 전파 설정이 나중에 바뀌어도
            // (또는 이 코드가 다른 트랜잭션 컨텍스트에서 재사용돼도) 위 버그가 재발하지 않게.
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
            retry.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return retry.execute(status -> {
                UserSpec existing = userSpecRepository.findByUser_Id(user.getId())
                        .orElseThrow(() -> e);
                // 경합에서 진 요청이 이긴 요청과 같은 내용이면(더블클릭·네트워크 재시도)
                // UPDATE를 생략한다 — insertAttempt 쪽 무변경 가드와 같은 이유.
                if (!hasChanges(existing, request)) {
                    return existing;
                }
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

    /**
     * 요청 값이 기존 엔티티와 동일한지 비교한다.
     * 동일하면 saveAndFlush()를 건너뛰어 @UpdateTimestamp가 updatedAt을 갱신하지 않게 한다.
     * updatedAt이 무변경 저장에도 바뀌면 RecommendationService.isSpecModifiedSince()가
     * 스펙 변경으로 판정해 추천 갱신 횟수를 헛되이 차감하는 문제가 있었다.
     */
    private boolean hasChanges(UserSpec existing, UserSpecRequest request) {
        // ⚠️ BigDecimal은 Objects.equals로 비교하면 안 된다 — equals가 scale까지 본다.
        // gpa 컬럼은 NUMERIC(4,2)라 DB에서 읽은 값은 3.80(scale 2)인데 FE는 3.8(scale 1)을
        // 보내므로, equals 비교면 무변경 저장이 전부 "변경"으로 판정돼 이 메서드의 목적
        // (updatedAt 보존 → 호출 횟수 미차감)이 실제 요청 경로에서 전혀 동작하지 않는다.
        // 값 기준 비교(compareTo)를 써야 한다.
        if (!bigDecimalValueEquals(existing.getGpa(), request.getGpa())) return true;
        if (!bigDecimalValueEquals(existing.getGpaMax(), request.getGpaMax())) return true;
        if (!Objects.equals(existing.getGrade(), request.getGrade())) return true;
        if (!Arrays.equals(existing.getCertifications(), convertCertifications(request))) return true;

        List<Map<String, Object>> oldLang = existing.getLanguageScores();
        List<Map<String, Object>> newLang = convertLanguageScores(request);
        if (!Objects.equals(oldLang, newLang)) return true;

        return false;
    }

    private boolean bigDecimalValueEquals(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
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
