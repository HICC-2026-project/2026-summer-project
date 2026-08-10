package com.career.recommendation.service;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신규 유저의 최초 스펙/목표직무 저장에서 동시 요청 두 개가 겹치면(find-then-save 경합)
 * user_specs/target_jobs의 UNIQUE(user_id) 제약을 위반해 500이 나던 문제를, 실제 로컬 DB로
 * 재현·검증한다. 이 테스트는 의도적으로 클래스 레벨 @Transactional을 걸지 않는다 — 걸면 두
 * 스레드가 같은 트랜잭션을 공유하게 되어 동시성 자체가 성립하지 않는다. 대신 각 테스트 끝에
 * 직접 정리한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class SpecTargetJobConcurrencyTest {

    @Autowired
    private UserSpecService userSpecService;
    @Autowired
    private TargetJobService targetJobService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSpecRepository userSpecRepository;
    @Autowired
    private TargetJobRepository targetJobRepository;

    private User testUser;

    private User createUser() {
        return userRepository.save(User.builder()
                .email("concurrency-test-" + System.nanoTime() + "@example.com")
                .nickname("동시성테스터")
                .provider("KAKAO")
                .providerId("provider-id-" + System.nanoTime())
                .build());
    }

    private Authentication authFor(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    }

    @AfterEach
    void cleanUp() {
        if (testUser == null) return;
        userSpecRepository.findByUser_Id(testUser.getId()).ifPresent(userSpecRepository::delete);
        targetJobRepository.findByUser_Id(testUser.getId()).ifPresent(targetJobRepository::delete);
        userRepository.deleteById(testUser.getId());
    }

    @Test
    void 스펙_최초_저장_동시_요청_두_건이_전부_예외_없이_성공하고_행은_하나만_남는다() throws Exception {
        testUser = createUser();
        Authentication auth = authFor(testUser.getId());

        UserSpecRequest request = new UserSpecRequest();
        request.setGpa(new BigDecimal("3.80"));
        request.setGpaMax(new BigDecimal("4.50"));
        request.setGrade(3);
        request.setLanguageScores(List.of());
        request.setCertifications(List.of());

        runConcurrently(() -> userSpecService.saveOrUpdateMySpec(auth, request));

        assertThat(userSpecRepository.findByUser_Id(testUser.getId())).isPresent();
    }

    @Test
    void 목표직무_최초_저장_동시_요청_두_건이_전부_예외_없이_성공하고_행은_하나만_남는다() throws Exception {
        testUser = createUser();
        Authentication auth = authFor(testUser.getId());

        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");

        runConcurrently(() -> targetJobService.saveOrUpdateMyTarget(auth, request));

        assertThat(targetJobRepository.findByUser_Id(testUser.getId())).isPresent();
    }

    private void runConcurrently(Runnable action) throws InterruptedException {
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                } catch (Throwable t) {
                    failures.incrementAndGet();
                    errors.add(t);
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(failures.get())
                .withFailMessage("동시 요청 중 %d건 실패: %s", failures.get(), errors)
                .isZero();
    }
}
