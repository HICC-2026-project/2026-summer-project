package com.career.recommendation.service;

import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSpecService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;

    @Transactional
    public UserSpecResponse saveOrUpdateMySpec(
            Authentication authentication,
            UserSpecRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId())
                .orElseGet(() -> UserSpec.builder()
                        .user(user)
                        .build());

        updateUserSpecFields(userSpec, request);

        UserSpec savedUserSpec = userSpecRepository.save(userSpec);

        return UserSpecResponse.from(savedUserSpec);
    }

    private void updateUserSpecFields(
            UserSpec userSpec,
            UserSpecRequest request
    ) {
        userSpec.setGpa(request.getGpa());
        userSpec.setGpaMax(request.getGpaMax());
        userSpec.setGrade(request.getGrade());
        userSpec.setLanguageScores(convertLanguageScores(request));
        userSpec.setCertifications(convertCertifications(request));
    }

    private List<Map<String, Object>> convertLanguageScores(
            UserSpecRequest request
    ) {
        if (request.getLanguageScores() == null) {
            return Collections.emptyList();
        }

        return request.getLanguageScores().stream()
                .filter(Objects::nonNull)
                .map(LanguageScoreRequest::toMap)
                .collect(Collectors.toList());
    }

    private String[] convertCertifications(UserSpecRequest request) {
        if (request.getCertifications() == null) {
            return new String[0];
        }

        return request.getCertifications().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(certification -> !certification.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
