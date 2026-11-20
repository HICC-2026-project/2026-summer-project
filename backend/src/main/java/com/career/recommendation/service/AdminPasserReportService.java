package com.career.recommendation.service;

import com.career.recommendation.domain.ReviewAction;
import com.career.recommendation.domain.ReviewStatus;
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

    private final PasserDataRepository passerDataRepository;
    private final LocalProofStorageService proofStorageService;
    private final JobSpecProfileService jobSpecProfileService;
    private final CurrentUserService currentUserService;

    /**
     * 함수 이름: list()
     * 기능: 검수 상태별 합격자 제보를 페이지 단위로 조회한다.
     * 파라미터: ReviewStatus status -> 조회할 검수 상태
     *           Pageable pageable -> 페이지 정보
     * 반환값: Page<AdminPasserReportResponse> -> 제보 목록 페이지
     */
    public Page<AdminPasserReportResponse> list(ReviewStatus status, Pageable pageable) {
        return passerDataRepository.findReportsByStatus(status.name(), pageable)
                .map(AdminPasserReportResponse::from);
    }

    /**
     * 함수 이름: get()
     * 기능: 제보 한 건을 ID로 조회한다. 사용자 제보(USER_REPORT)가 아니거나 없으면 예외.
     * 파라미터: UUID reportId -> 제보 ID
     * 반환값: AdminPasserReportResponse -> 제보 상세 응답
     * 예외: PasserReportNotFoundException -> 해당 제보가 없을 때
     */
    public AdminPasserReportResponse get(UUID reportId) {
        return AdminPasserReportResponse.from(find(reportId));
    }

    /**
     * 함수 이름: proof()
     * 기능: 제보의 증빙 파일과 content-type을 돌려준다. 파일이 디스크에 없으면 empty — 컨트롤러가 404로 바꾼다.
     * 파라미터: UUID reportId -> 제보 ID
     * 반환값: Optional<ProofFile> -> 증빙 파일 정보(파일이 없으면 empty)
     * 예외: PasserReportNotFoundException -> 해당 제보가 없을 때
     */
    public Optional<ProofFile> proof(UUID reportId) {
        PasserData report = find(reportId);
        return proofStorageService.load(report.getProofStoredName())
                .map(resource -> new ProofFile(resource, report.getProofContentType(), report.getProofOriginalName()));
    }

    /**
     * 함수 이름: review()
     * 기능: 제보를 승인 또는 반려 처리하고 검수자·검수 시각·반려 사유를 기록한다. 비교 가능 집합(isVerified=true)이 바뀐 경우에만 직무 프로필 캐시를 비운다.
     * 파라미터: Authentication authentication -> 현재 로그인한 검수 관리자 인증 정보
     *           UUID reportId -> 제보 ID
     *           PasserReviewRequest request -> 검수 요청(승인/반려 액션, 반려 사유)
     * 반환값: AdminPasserReportResponse -> 검수 반영 후 제보 응답
     * 예외: PasserReportNotFoundException -> 해당 제보가 없을 때
     */
    @Transactional
    public AdminPasserReportResponse review(Authentication authentication, UUID reportId, PasserReviewRequest request) {
        User reviewer = currentUserService.getCurrentUser(authentication);
        PasserData report = find(reportId);

        boolean approve = request.getAction() == ReviewAction.APPROVE;
        boolean wasVerified = Boolean.TRUE.equals(report.getIsVerified());

        // 관리 엔티티라 트랜잭션 커밋 시 dirty checking으로 UPDATE된다 — 별도 save 불필요.
        report.setIsVerified(approve);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewedBy(reviewer);
        report.setRejectReason(approve ? null : request.getReason().trim());

        // 비교 가능 집합(isVerified=true)이 바뀌었을 때만 캐시를 비운다 — 최초 승인, 승인↔반려 전환.
        // 이미 반려된 걸 다시 반려하는 건 분포에 영향이 없다.
        if (approve != wasVerified) {
            jobSpecProfileService.evictAll();
        }
        log.info("합격자 제보 검수: id={}, action={}, reviewer={}", reportId, request.getAction(), reviewer.getId());
        return AdminPasserReportResponse.from(report);
    }

    /**
     * 함수 이름: find()
     * 기능: ID로 합격자 데이터를 찾되, 사용자 제보(USER_REPORT) 출처인 것만 인정한다.
     * 파라미터: UUID reportId -> 제보 ID
     * 반환값: PasserData -> 조회된 제보 엔티티
     * 예외: PasserReportNotFoundException -> 없거나 사용자 제보가 아닐 때
     */
    private PasserData find(UUID reportId) {
        return passerDataRepository.findById(reportId)
                .filter(p -> PasserData.ORIGIN_USER_REPORT.equals(p.getDataOrigin()))
                .orElseThrow(() -> new PasserReportNotFoundException(reportId));
    }

    public record ProofFile(Resource resource, String contentType, String originalName) {
    }
}
