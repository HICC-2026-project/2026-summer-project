package com.career.recommendation.service;

import com.career.recommendation.entity.User;
import com.career.recommendation.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQUIRES_NEW로 커밋되는 카운터라 테스트 트랜잭션에 합류시키지 않는다(합류하면 커밋·경합이 보이지 않는다).
 * 사용자 행을 실제로 만들고 끝에 지운다 — ai_daily_attempts는 FK CASCADE로 함께 사라진다.
 */
@SpringBootTest
@ActiveProfiles("local")
class AiDailyAttemptLimiterTest {

    @Autowired private AiDailyAttemptLimiter limiter;
    @Autowired private UserRepository userRepository;

    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId != null) userRepository.deleteById(userId);
    }

    @Test
    void 사용자_종류별로_하루_3회까지_허용하고_4회째부터_거절하며_종류는_서로_독립이다() {
        userId = newUser();

        assertThat(limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isTrue();
        assertThat(limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isTrue();
        assertThat(limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isTrue();
        assertThat(limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isFalse();
        assertThat(limiter.attemptsToday(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isEqualTo(4);

        // 로드맵 카운터는 별개
        assertThat(limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_ROADMAP)).isTrue();
    }

    @Test
    void 동시_요청에서도_허용_횟수가_상한을_넘지_않는다() throws InterruptedException {
        // 예전 read-then-write 카운터는 동시 요청이 증가분을 잃어 한도가 느슨해졌다(코드 주석의 알려진 경합).
        // ON CONFLICT ... DO UPDATE ... RETURNING은 행 단위로 직렬화되므로 정확히 3회만 허용돼야 한다.
        userId = newUser();
        int threads = 12;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger granted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)) granted.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertThat(granted.get()).isEqualTo(3);
        assertThat(limiter.attemptsToday(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).isEqualTo(threads);
    }

    private UUID newUser() {
        return userRepository.save(User.builder()
                .nickname("limit").provider("KAKAO").providerId("limit-" + System.nanoTime()).build()).getId();
    }
}
