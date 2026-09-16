package com.career.recommendation.service;

import com.career.recommendation.domain.ReviewAction;
import com.career.recommendation.dto.admin.AdminPasserReportResponse;
import com.career.recommendation.dto.admin.PasserReviewRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.PasserReportNotFoundException;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasserReportServiceTest {

    @Mock private PasserDataRepository passerDataRepository;
    @Mock private LocalProofStorageService proofStorageService;
    @Mock private JobSpecProfileService jobSpecProfileService;
    @Mock private CurrentUserService currentUserService;
    @Mock private Authentication authentication;

    @InjectMocks
    private AdminPasserReportService service;

    private final User admin = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("admin").role("ADMIN").build();

    @Test
    void 승인하면_isVerified가_켜지고_프로필_캐시를_비운다() {
        PasserData report = pendingReport();
        stubFind(report);

        AdminPasserReportResponse result = service.review(authentication, report.getId(), request(ReviewAction.APPROVE, null));

        assertThat(report.getIsVerified()).isTrue();
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(report.getReviewedBy()).isSameAs(admin);
        assertThat(report.getRejectReason()).isNull();
        assertThat(result.getStatus()).isEqualTo("VERIFIED");
        // 승인 = 비교 가능 집합이 바뀜 → 다음 비교 요청이 새 분포를 보게 즉시 무효화
        verify(jobSpecProfileService).evictAll();
    }

    @Test
    void 반려하면_사유를_남기고_데이터는_보존하며_캐시는_건드리지_않는다() {
        PasserData report = pendingReport();
        stubFind(report);

        AdminPasserReportResponse result = service.review(authentication, report.getId(), request(ReviewAction.REJECT, "  증빙 불일치 "));

        assertThat(report.getIsVerified()).isFalse();
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(report.getRejectReason()).isEqualTo("증빙 불일치");
        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(passerDataRepository, never()).delete(any());
        // 미검수(미반영) → 반려(미반영): 분포가 안 바뀌므로 캐시 무효화 불필요
        verify(jobSpecProfileService, never()).evictAll();
    }

    @Test
    void 승인된_제보를_반려로_되돌리면_캐시를_비운다() {
        PasserData report = pendingReport();
        report.setIsVerified(true);
        stubFind(report);

        service.review(authentication, report.getId(), request(ReviewAction.REJECT, "재검토 결과 불일치"));

        assertThat(report.getIsVerified()).isFalse();
        verify(jobSpecProfileService).evictAll();
    }

    @Test
    void 사용자_제보가_아닌_DEMO_데이터는_검수_대상이_아니다() {
        PasserData demo = pendingReport();
        demo.setDataOrigin("DEMO");
        when(currentUserService.getCurrentUser(authentication)).thenReturn(admin);
        when(passerDataRepository.findById(demo.getId())).thenReturn(Optional.of(demo));

        assertThatThrownBy(() -> service.review(authentication, demo.getId(), request(ReviewAction.APPROVE, null)))
                .isInstanceOf(PasserReportNotFoundException.class);
        verify(jobSpecProfileService, never()).evictAll();
    }

    @Test
    void 응답에는_제보자_식별자가_없다() {
        // AdminPasserReportResponse에 reporter 필드 자체가 없어야 한다 — 검수에 불필요한 개인 연결 정보.
        assertThat(AdminPasserReportResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("reporter", "reporterId", "reporterUserId");
    }

    private void stubFind(PasserData report) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(admin);
        when(passerDataRepository.findById(report.getId())).thenReturn(Optional.of(report));
    }

    private PasserData pendingReport() {
        return PasserData.builder()
                .id(UUID.randomUUID())
                .jobType("BACKEND").year(2026)
                .isVerified(false)
                .dataOrigin("USER_REPORT")
                .reporter(User.builder().id(UUID.randomUUID()).build())
                .build();
    }

    private PasserReviewRequest request(ReviewAction action, String reason) {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction(action);
        r.setReason(reason);
        return r;
    }
}
