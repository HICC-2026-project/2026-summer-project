package com.career.recommendation.service;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.admin.AdminPasserEntryRequest;
import com.career.recommendation.dto.admin.AdminPasserEntryResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 관리자 합격자 수기 등록 — 공개된 커뮤니티·블로그 합격 스펙 후기를 관리자가 직접 입력해
 * 합격자 표본 부족을 보완한다. 사용자 제보(PasserReportService)와 달리 검수 절차가 없다
 * (관리자 신뢰) — 저장 즉시 isVerified=true로 비교 가능 집합에 들어간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPasserEntryService {

    private final CurrentUserService currentUserService;
    private final PasserDataRepository passerDataRepository;
    private final JobSpecProfileService jobSpecProfileService;

    /**
     * 함수 이름: create()
     * 기능: 관리자가 입력한 합격자 스펙을 검수 없이 isVerified=true로 저장하고, 직무 프로필 캐시를 무효화한다.
     * 파라미터: Authentication authentication -> 현재 로그인한 관리자 인증 정보
     *           AdminPasserEntryRequest request -> 수기 등록 요청(직무·연도·학점·어학·자격증·경험 수·출처 메모)
     * 반환값: AdminPasserEntryResponse -> 등록된 합격자 데이터 응답
     */
    @Transactional
    public AdminPasserEntryResponse create(Authentication authentication, AdminPasserEntryRequest request) {
        User admin = currentUserService.getCurrentUser(authentication);
        String jobType = JobType.of(request.getJobType()).name();
        LocalDateTime now = LocalDateTime.now();

        PasserData entry = PasserData.builder()
                .activity(null)
                .reporter(null)
                .jobType(jobType)
                .year(request.getYear())
                .gpa(request.getGpa())
                .gpaMax(request.getGpaMax())
                .languageScores(convertLanguageScores(request.getLanguageScores()))
                .certifications(convertCertifications(request.getCertifications()))
                .experienceCount(request.getExperienceCount())
                .specSummary(null)
                .isVerified(true)
                .dataOrigin(PasserData.ORIGIN_ADMIN_ENTRY)
                .sourceNote(request.getSourceNote().trim())
                .reviewedAt(now)
                .reviewedBy(admin)
                .rejectReason(null)
                .build();

        PasserData saved = passerDataRepository.save(entry);

        // 저장 즉시 비교 가능 집합(isVerified=true)에 들어가므로 항상 무효화한다
        // (검수 승인 경로 — AdminPasserReportService.review와 동일한 이유).
        jobSpecProfileService.evictAll();

        log.info("관리자 합격자 수기 등록: id={}, jobType={}, admin={}", saved.getId(), jobType, admin.getId());
        return AdminPasserEntryResponse.created(saved);
    }

    /**
     * 함수 이름: convertLanguageScores()
     * 기능: 어학 점수 요청 목록에서 null을 제외하고 저장용 Map 목록으로 변환한다. 입력이 null이면 빈 목록을 돌려준다.
     * 파라미터: List<LanguageScoreRequest> languageScores -> 어학 점수 요청 목록(null 가능)
     * 반환값: List<Map<String, Object>> -> 저장용 어학 점수 Map 목록
     */
    private List<Map<String, Object>> convertLanguageScores(List<LanguageScoreRequest> languageScores) {
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
