package com.career.recommendation.service;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.passer.MyPasserReportResponse;
import com.career.recommendation.dto.passer.PasserReportRequest;
import com.career.recommendation.dto.passer.PasserReportResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.DuplicatePasserReportException;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasserReportService {

    /** 한 계정의 24시간 제보 상한. */
    private static final int MAX_REPORTS_PER_DAY = 5;

    private final CurrentUserService currentUserService;
    private final PasserDataRepository passerDataRepository;
    private final LocalProofStorageService localProofStorageService;

    @Transactional
    public PasserReportResponse submit(
            Authentication authentication,
            PasserReportRequest request,
            MultipartFile proof
    ) {
        // JWT만 유효한 것이 아니라 현재 DB에 존재하는 로그인 사용자인지도 확인한다.
        User reporter = currentUserService.getCurrentUser(authentication);
        String jobType = JobType.of(request.getJobType()).name();

        // 파일을 디스크에 쓰기 전에 중복·상한을 먼저 본다 — 거절될 요청의 증빙을 저장했다 지우지 않게.
        rejectIfDuplicate(reporter, jobType, request.getYear());

        LocalProofStorageService.StoredProof storedProof = localProofStorageService.store(proof);

        PasserData report = PasserData.builder()
                .activity(null)
                .reporter(reporter)
                .jobType(jobType)
                .year(request.getYear())
                .gpa(request.getGpa())
                .gpaMax(request.getGpaMax())
                .languageScores(convertLanguageScores(request.getLanguageScores()))
                .certifications(convertCertifications(request.getCertifications()))
                .experienceCount(request.getExperienceCount())
                .specSummary(null)
                .isVerified(false)
                .dataOrigin(PasserData.ORIGIN_USER_REPORT)
                .proofOriginalName(storedProof.originalName())
                .proofStoredName(storedProof.storedName())
                .proofContentType(storedProof.contentType())
                .proofFileSize(storedProof.fileSize())
                .build();

        try {
            // 파일은 DB 트랜잭션 대상이 아니므로 즉시 flush해 DB 오류를 여기서 감지한다.
            PasserData savedReport = passerDataRepository.saveAndFlush(report);
            return PasserReportResponse.pending(savedReport);
        } catch (RuntimeException exception) {
            // DB 저장이 실패하면 먼저 저장한 로컬 파일을 제거해 고아 파일을 남기지 않는다.
            localProofStorageService.deleteQuietly(storedProof.storedName());
            throw exception;
        }
    }

    /** 본인이 제보한 목록(최신순). 검수 여부만 확인하는 용도. */
    public List<MyPasserReportResponse> findMyReports(Authentication authentication) {
        User me = currentUserService.getCurrentUser(authentication);
        return passerDataRepository.findAllByReporter_IdOrderByCreatedAtDesc(me.getId()).stream()
                .map(MyPasserReportResponse::from)
                .toList();
    }

    /**
     * 중복 제보 규칙.
     * 1) 같은 직무·연도로 검수 대기 중인 제보가 있으면 거절 — 같은 합격 사실을 두 번 세지 않는다.
     *    반려된 뒤 고쳐서 다시 내는 것은 허용(reviewedAt != null이면 존재 검사에서 빠짐).
     * 2) 24시간 내 MAX_REPORTS_PER_DAY건 초과 거절 — 검수 큐를 한 계정이 채우는 것을 막는다.
     */
    private void rejectIfDuplicate(User reporter, String jobType, Integer year) {
        if (passerDataRepository.existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull(reporter.getId(), jobType, year)) {
            throw new DuplicatePasserReportException(
                    "같은 직무·연도로 검수 대기 중인 제보가 이미 있습니다. 검수가 끝난 뒤 다시 제보할 수 있습니다.");
        }
        long recent = passerDataRepository.countByReporter_IdAndCreatedAtAfter(
                reporter.getId(), LocalDateTime.now().minusHours(24));
        if (recent >= MAX_REPORTS_PER_DAY) {
            throw new DuplicatePasserReportException(
                    "하루에 " + MAX_REPORTS_PER_DAY + "건까지만 제보할 수 있습니다. 내일 다시 시도해 주세요.");
        }
    }

    private List<Map<String, Object>> convertLanguageScores(
            List<LanguageScoreRequest> languageScores
    ) {
        if (languageScores == null) {
            return Collections.emptyList();
        }

        return languageScores.stream()
                .filter(Objects::nonNull)
                .map(LanguageScoreRequest::toMap)
                .toList();
    }

    private String[] convertCertifications(List<String> certifications) {
        if (certifications == null) {
            return new String[0];
        }

        return certifications.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(certification -> !certification.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
