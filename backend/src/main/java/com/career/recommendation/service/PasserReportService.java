package com.career.recommendation.service;

import com.career.recommendation.dto.passer.PasserReportRequest;
import com.career.recommendation.dto.passer.PasserReportResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasserReportService {

    private static final String USER_REPORT = "USER_REPORT";

    private final CurrentUserService currentUserService;
    private final PasserDataRepository passerDataRepository;

    @Transactional
    public PasserReportResponse submit(
            Authentication authentication,
            PasserReportRequest request
    ) {
        // JWT만 유효한 것이 아니라 현재 DB에 존재하는 로그인 사용자인지도 확인한다.
        currentUserService.getCurrentUser(authentication);

        PasserData report = PasserData.builder()
                .activity(null)
                .jobType(request.getJobType().trim().toUpperCase(Locale.ROOT))
                .year(request.getYear())
                .gpa(request.getGpa())
                .gpaMax(request.getGpaMax())
                .languageScores(convertLanguageScores(request.getLanguageScores()))
                .certifications(convertCertifications(request.getCertifications()))
                .experienceCount(request.getExperienceCount())
                .specSummary(null)
                .isVerified(false)
                .dataOrigin(USER_REPORT)
                .build();

        PasserData savedReport = passerDataRepository.save(report);
        return PasserReportResponse.pending(savedReport);
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
