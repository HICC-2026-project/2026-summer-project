package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProofRetentionSchedulerTest {

    @Mock private PasserDataRepository passerDataRepository;
    @Mock private LocalProofStorageService proofStorageService;
    @InjectMocks private ProofRetentionScheduler scheduler;

    @Test
    void 기한_지난_검수_완료_제보의_파일을_지우고_증빙_메타를_비운다() {
        PasserData report = PasserData.builder().id(UUID.randomUUID())
                .proofStoredName("old.png").proofOriginalName("합격.png").proofContentType("image/png").proofFileSize(10L)
                .reviewedAt(LocalDateTime.now().minusDays(40)).isVerified(true).build();
        LocalDateTime before = LocalDateTime.now().minusDays(30);
        when(passerDataRepository.findReviewedWithProofBefore(before)).thenReturn(List.of(report));

        int purged = scheduler.purgeReviewedBefore(before);

        assertThat(purged).isEqualTo(1);
        verify(proofStorageService).deleteQuietly("old.png");
        // 스펙 데이터는 남고 증빙 메타만 비워진다
        assertThat(report.getProofStoredName()).isNull();
        assertThat(report.getProofOriginalName()).isNull();
        assertThat(report.getProofContentType()).isNull();
        assertThat(report.getProofFileSize()).isNull();
        assertThat(report.getIsVerified()).isTrue();
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        when(passerDataRepository.findReviewedWithProofBefore(any())).thenReturn(List.of());
        assertThat(scheduler.purgeReviewedBefore(LocalDateTime.now())).isZero();
    }
}
