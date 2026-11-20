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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasserReportService {

    /** 한 계정의 24시간 제보 상한. */
    private static final int MAX_REPORTS_PER_DAY = 5;

    private final CurrentUserService currentUserService;
    private final PasserDataRepository passerDataRepository;
    private final LocalProofStorageService localProofStorageService;
    private final PasserGithubContributionRunner passerGithubContributionRunner;

    /**
     * 함수 이름: submit()
     * 기능: 합격자 제보를 접수한다. 중복·일일 상한을 먼저 검사하고 증빙 파일을 저장한 뒤 검수 대기(isVerified=false) 상태로 DB에 저장한다.
     *       DB 저장이 실패하면 저장해 둔 증빙 파일을 지우고, GitHub 아이디 입력·동의 시 저장 후 비동기 분석을 시작한다.
     * 파라미터: Authentication authentication -> 현재 로그인한 제보자 인증 정보
     *           PasserReportRequest request -> 제보 요청(직무·연도·학점·어학·자격증·경험 수·GitHub 정보)
     *           MultipartFile proof -> 증빙 이미지 파일
     * 반환값: PasserReportResponse -> 검수 대기 상태의 제보 응답
     * 예외: DuplicatePasserReportException -> 검수 대기 중복 제보이거나 24시간 상한 초과일 때
     */
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

        PasserData savedReport;
        try {
            // 파일은 DB 트랜잭션 대상이 아니므로 즉시 flush해 DB 오류를 여기서 감지한다.
            savedReport = passerDataRepository.saveAndFlush(report);
        } catch (RuntimeException exception) {
            // DB 저장이 실패하면 먼저 저장한 로컬 파일을 제거해 고아 파일을 남기지 않는다.
            localProofStorageService.deleteQuietly(storedProof.storedName());
            throw exception;
        }

        // E11-5 — GitHub 아이디를 입력하고 동의했을 때만, 제보 저장이 끝난 뒤 비동기로 분석을
        // 시작한다. username은 이 지역 변수 밖으로 나가지 않는다(로그·엔티티 어디에도 남기지
        // 않음 — PasserGithubContributionRunner의 클래스 주석 참고). 분석 시작 자체가 실패해도
        // (예: 실행기 큐 포화) 이미 저장된 제보는 그대로 유효하다.
        String githubUsername = request.getGithubUsername() != null ? request.getGithubUsername().trim() : null;
        if (githubUsername != null && !githubUsername.isBlank() && Boolean.TRUE.equals(request.getGithubConsent())) {
            try {
                passerGithubContributionRunner.analyze(savedReport.getId(), githubUsername, request.getYear());
            } catch (RuntimeException e) {
                log.warn("합격자 제보 GitHub 분석 시작 실패 (reportId={}): {}", savedReport.getId(), e.getClass().getSimpleName());
            }
        }

        return PasserReportResponse.pending(savedReport);
    }

    /**
     * 함수 이름: findMyReports()
     * 기능: 본인이 제보한 목록(최신순)을 조회한다. 검수 여부만 확인하는 용도.
     * 파라미터: Authentication authentication -> 현재 로그인한 사용자 인증 정보
     * 반환값: List<MyPasserReportResponse> -> 내 제보 목록(최신순)
     */
    public List<MyPasserReportResponse> findMyReports(Authentication authentication) {
        User me = currentUserService.getCurrentUser(authentication);
        return passerDataRepository.findAllByReporter_IdOrderByCreatedAtDesc(me.getId()).stream()
                .map(MyPasserReportResponse::from)
                .toList();
    }

    /**
     * 함수 이름: rejectIfDuplicate()
     * 기능: 중복 제보 규칙을 검사해 위반이면 예외를 던진다.
     * 중복 제보 규칙.
     * 1) 같은 직무·연도로 검수 대기 중인 제보가 있으면 거절 — 같은 합격 사실을 두 번 세지 않는다.
     *    반려된 뒤 고쳐서 다시 내는 것은 허용(reviewedAt != null이면 존재 검사에서 빠짐).
     * 2) 24시간 내 MAX_REPORTS_PER_DAY건 초과 거절 — 검수 큐를 한 계정이 채우는 것을 막는다.
     *
     * 파라미터: User reporter -> 제보자
     *           String jobType -> 직무 코드(정규화된 enum name)
     *           Integer year -> 합격 연도
     * 반환값: 없음
     * 예외: DuplicatePasserReportException -> 검수 대기 중복 제보이거나 24시간 상한 초과일 때
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

    /**
     * 함수 이름: convertLanguageScores()
     * 기능: 어학 점수 요청 목록에서 null을 제외하고 저장용 Map 목록으로 변환한다. 입력이 null이면 빈 목록을 돌려준다.
     * 파라미터: List<LanguageScoreRequest> languageScores -> 어학 점수 요청 목록(null 가능)
     * 반환값: List<Map<String, Object>> -> 저장용 어학 점수 Map 목록
     */
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

    /**
     * 함수 이름: convertCertifications()
     * 기능: 자격증 목록에서 null·공백 항목을 제거하고 앞뒤 공백을 다듬어 중복 없는 배열로 변환한다. 입력이 null이면 빈 배열을 돌려준다.
     * 파라미터: List<String> certifications -> 자격증 이름 목록(null 가능)
     * 반환값: String[] -> 정제된 자격증 배열
     */
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
