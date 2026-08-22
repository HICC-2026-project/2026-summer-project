package com.career.recommendation.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gemini 호출 결과 집계(프로세스 기동 이후 누적). 외부 메트릭 시스템이 없는 동안 운영 요약 API에서 읽는다.
 * 실패율이 갑자기 오르면 키 만료·모델명 변경·429를 의심할 수 있고, 평균 지연이 30초에 붙으면 타임아웃 조정 신호다.
 */
@Component
public class GeminiCallStats {

    private final LongAdder success = new LongAdder();
    private final LongAdder failure = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong();
    private final AtomicLong lastFailureEpochMs = new AtomicLong();

    public void recordSuccess(long latencyMs) {
        success.increment();
        record(latencyMs);
    }

    public void recordFailure(long latencyMs) {
        failure.increment();
        lastFailureEpochMs.set(System.currentTimeMillis());
        record(latencyMs);
    }

    private void record(long latencyMs) {
        totalLatencyMs.add(latencyMs);
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
    }

    public Snapshot snapshot() {
        long ok = success.sum();
        long fail = failure.sum();
        long total = ok + fail;
        return new Snapshot(ok, fail,
                total == 0 ? 0 : totalLatencyMs.sum() / total,
                maxLatencyMs.get(),
                lastFailureEpochMs.get() == 0 ? null : lastFailureEpochMs.get());
    }

    public record Snapshot(long success, long failure, long avgLatencyMs, long maxLatencyMs, Long lastFailureEpochMs) {
        public double failureRate() {
            long total = success + failure;
            return total == 0 ? 0.0 : (double) failure / total;
        }
    }
}
