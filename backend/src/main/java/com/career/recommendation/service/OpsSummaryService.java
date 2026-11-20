package com.career.recommendation.service;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.admin.OpsSummaryResponse;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.util.SpecPositionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpsSummaryService {

    private final PasserDataRepository passerDataRepository;
    private final GeminiDailyQuota dailyQuota;
    private final GeminiCallStats callStats;

    /**
     * 함수 이름: summarize()
     * 기능: 운영 요약을 만든다. 검수 대기 제보 수, 직무별 비교 가능 합격자 수, Gemini 일일 사용량·호출 통계를 모은다.
     *       6개 직무 전부를 0으로 깔아두고 집계를 덮는다 — 데이터가 없는 직무가 목록에서 빠지면 "부족"이 안 보이기 때문이다.
     * 파라미터: 없음
     * 반환값: OpsSummaryResponse -> 운영 요약 응답
     */
    public OpsSummaryResponse summarize() {
        // 6개 직무 전부를 0으로 깔아두고 집계를 덮는다 — 데이터가 없는 직무가 목록에서 빠지면 "부족"이 안 보인다.
        Map<String, Long> byJob = new LinkedHashMap<>();
        for (JobType job : JobType.values()) {
            byJob.put(job.name(), 0L);
        }
        for (Object[] row : passerDataRepository.countComparableByJobType()) {
            if (row[0] != null) {
                byJob.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        }

        GeminiCallStats.Snapshot s = callStats.snapshot();
        return OpsSummaryResponse.builder()
                .pendingReports(passerDataRepository.countPendingReports())
                .comparablePassersByJob(byJob)
                .minSampleSize(SpecPositionCalculator.MIN_SAMPLE)
                .gemini(OpsSummaryResponse.Gemini.builder()
                        .usedToday(dailyQuota.usedToday())
                        .dailyLimit(dailyQuota.dailyLimit())
                        .success(s.success())
                        .failure(s.failure())
                        .failureRate(s.failureRate())
                        .avgLatencyMs(s.avgLatencyMs())
                        .maxLatencyMs(s.maxLatencyMs())
                        .lastFailureEpochMs(s.lastFailureEpochMs())
                        .build())
                .build();
    }
}
