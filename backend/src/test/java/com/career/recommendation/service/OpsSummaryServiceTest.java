package com.career.recommendation.service;

import com.career.recommendation.dto.admin.OpsSummaryResponse;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsSummaryServiceTest {

    @Mock private PasserDataRepository passerDataRepository;

    @Test
    void 직무_6종이_모두_나오고_데이터_없는_직무는_0이다() {
        when(passerDataRepository.countComparableByJobType())
                .thenReturn(List.<Object[]>of(new Object[]{"BACKEND", 7L}, new Object[]{"SECURITY", 2L}));
        when(passerDataRepository.countPendingReports()).thenReturn(3L);
        GeminiDailyQuota quota = new GeminiDailyQuota(100);
        quota.tryAcquire();
        GeminiCallStats stats = new GeminiCallStats();
        stats.recordSuccess(1200);
        stats.recordFailure(30000);

        OpsSummaryResponse s = new OpsSummaryService(passerDataRepository, quota, stats).summarize();

        assertThat(s.getPendingReports()).isEqualTo(3);
        assertThat(s.getComparablePassersByJob())
                .containsEntry("BACKEND", 7L).containsEntry("SECURITY", 2L)
                .containsEntry("FRONTEND", 0L).containsEntry("PM", 0L)
                .hasSize(6);
        assertThat(s.getMinSampleSize()).isEqualTo(3);
        assertThat(s.getGemini().getUsedToday()).isEqualTo(1);
        assertThat(s.getGemini().getDailyLimit()).isEqualTo(100);
        assertThat(s.getGemini().getSuccess()).isEqualTo(1);
        assertThat(s.getGemini().getFailure()).isEqualTo(1);
        assertThat(s.getGemini().getFailureRate()).isEqualTo(0.5);
        assertThat(s.getGemini().getAvgLatencyMs()).isEqualTo(15600);
        assertThat(s.getGemini().getMaxLatencyMs()).isEqualTo(30000);
        assertThat(s.getGemini().getLastFailureEpochMs()).isNotNull();
    }
}
