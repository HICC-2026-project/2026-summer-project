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

    /**
     * 함수 이름: recordSuccess()
     * 기능: Gemini 호출 성공 1건을 집계하고 지연 시간을 누적한다.
     * 파라미터: long latencyMs -> 호출에 걸린 시간(밀리초)
     * 반환값: 없음
     */
    public void recordSuccess(long latencyMs) {
        success.increment();
        record(latencyMs);
    }

    /**
     * 함수 이름: recordFailure()
     * 기능: Gemini 호출 실패 1건을 집계하고 마지막 실패 시각을 현재 시각으로 갱신하며 지연 시간을 누적한다.
     * 파라미터: long latencyMs -> 실패까지 걸린 시간(밀리초)
     * 반환값: 없음
     */
    public void recordFailure(long latencyMs) {
        failure.increment();
        lastFailureEpochMs.set(System.currentTimeMillis());
        record(latencyMs);
    }

    /**
     * 함수 이름: record()
     * 기능: 성공·실패 공통으로 총 지연 시간을 더하고 최대 지연 시간을 갱신한다.
     * 파라미터: long latencyMs -> 호출에 걸린 시간(밀리초)
     * 반환값: 없음
     */
    private void record(long latencyMs) {
        totalLatencyMs.add(latencyMs);
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
    }

    /**
     * 함수 이름: snapshot()
     * 기능: 현재까지의 성공·실패 건수, 평균·최대 지연, 마지막 실패 시각을 한 시점의 값으로 묶어 돌려준다.
     *       호출이 한 번도 없으면 평균 지연은 0, 실패한 적이 없으면 마지막 실패 시각은 null이다.
     * 파라미터: 없음
     * 반환값: Snapshot -> 집계 스냅샷
     */
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
        /**
         * 함수 이름: failureRate()
         * 기능: 전체 호출 중 실패 비율을 계산한다. 호출이 없으면 0.0.
         * 파라미터: 없음
         * 반환값: double -> 실패율(0.0 ~ 1.0)
         */
        public double failureRate() {
            long total = success + failure;
            return total == 0 ? 0.0 : (double) failure / total;
        }
    }
}
