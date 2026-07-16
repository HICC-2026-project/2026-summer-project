package com.career.recommendation.service;

import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    private void updateUserSpecFields(UserSpec userSpec, UserSpecRequest request) {
        userSpec.setGpa(request.getGpa());

        if (request.getGpaMax() != null) {
            userSpec.setGpaMax(request.getGpaMax());
        } else if (userSpec.getGpaMax() == null) {
            userSpec.setGpaMax(new BigDecimal("4.5"));
        }

        userSpec.setLanguageScores(request.getLanguageScores());
        userSpec.setCertifications(request.getCertifications());
        userSpec.setGrade(request.getGrade());
    }
}