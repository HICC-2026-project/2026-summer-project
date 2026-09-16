package com.career.recommendation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiDailyQuotaTest {

    @Test
    void 상한까지는_허용하고_넘으면_거절한다() {
        GeminiDailyQuota quota = new GeminiDailyQuota(3);

        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isFalse();
        assertThat(quota.tryAcquire()).isFalse();
        // 거절된 시도는 사용량에 포함되지 않는다
        assertThat(quota.usedToday()).isEqualTo(3);
    }

    @Test
    void 상한이_0이하면_제한하지_않는다() {
        GeminiDailyQuota quota = new GeminiDailyQuota(0);
        for (int i = 0; i < 1000; i++) {
            assertThat(quota.tryAcquire()).isTrue();
        }
    }

    @Test
    void 동시_요청에서도_상한을_넘기지_않는다() throws InterruptedException {
        GeminiDailyQuota quota = new GeminiDailyQuota(50);
        java.util.concurrent.atomic.AtomicInteger granted = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] threads = new Thread[20];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    if (quota.tryAcquire()) granted.incrementAndGet();
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();

        assertThat(granted.get()).isEqualTo(50);
        assertThat(quota.usedToday()).isEqualTo(50);
    }
}
