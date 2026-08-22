package com.career.recommendation.service;

import com.career.recommendation.dto.admin.AdminPasserReportResponse;
import com.career.recommendation.dto.admin.PasserReviewRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.PasserReportNotFoundException;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 합격자 제보 검수 — 제보(USER_REPORT) → 승인/반려 → 직무 프로필 캐시 무효화.
 * 이 서비스가 없던 동안 제보는 영원히 미검수로 남아 비교 데이터에 한 건도 반영되지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPasserReportService {

    private static final String USER_REPORT = "USER_REPORT";

    private final PasserDataRepository passerDataRepository;
    private final LocalProofStorageService proofStorageService;
    private final JobSpecProfileService jobSpecProfileService;
    private final CurrentUserService currentUserService;

    public Page<AdminPasserReportResponse> list(String status, Pageable pageable) {
        String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        Page<PasserData> page = switch (normalized) {
            case "PENDING" -> passerDataRepository.findPendingReports(pageable);
            case "VERIFIED" -> passerDataRepository.findVerifiedReports(pageable);
            case "REJECTED" -> passerDataRepository.findRejectedReports(pageable);
            default -> throw new IllegalArgumentException("status는 PENDING, VERIFIED, REJECTED 중 하나여야 합니다.");
        };
        return page.map(AdminPasserReportResponse::from);
    }

    public AdminPasserReportResponse get(UUID reportId) {
        return AdminPasserReportResponse.from(find(reportId));
    }

    /** 증빙 파일과 content-type. 파일이 디스크에 없으면 empty — 컨트롤러가 404로 바꾼다. */
    public Optional<ProofFile> proof(UUID reportId) {
        PasserData report = find(reportId);
        return proofStorageService.load(report.getProofStoredName())
                .map(resource -> new ProofFile(resource, report.getProofContentType(), report.getProofOriginalName()));
    }

    @Transactional
    public AdminPasserReportResponse review(Authentication authentication, UUID reportId, PasserReviewRequest request) {
        User reviewer = currentUserService.getCurrentUser(authentication);
        PasserData report = find(reportId);

        boolean approve = "APPROVE".equals(request.getAction());
        boolean wasVerified = Boolean.TRUE.equals(report.getIsVerified());

        report.setIsVerified(approve);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewedBy(reviewer);
        report.setRejectReason(approve ? null : request.getReason().trim());
        PasserData saved = passerDataRepository.saveAndFlush(report);

        // 비교 가능 집합(isVerified=true)이 바뀌었을 때만 캐시를 비운다 — 최초 승인, 승인↔반려 전환.
        // 이미 반려된 걸 다시 반려하는 건 분포에 영향이 없다.
        if (approve != wasVerified) {
            jobSpecProfileService.evictAll();
        }
        log.info("합격자 제보 검수: id={}, action={}, reviewer={}", reportId, request.getAction(), reviewer.getId());
        return AdminPasserReportResponse.from(saved);
    }

    private PasserData find(UUID reportId) {
        return passerDataRepository.findById(reportId)
                .filter(p -> USER_REPORT.equals(p.getDataOrigin()))
                .orElseThrow(() -> new PasserReportNotFoundException(reportId));
    }

    public record ProofFile(Resource resource, String contentType, String originalName) {
    }
}
