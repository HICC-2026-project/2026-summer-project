package com.career.recommendation.service;

import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSpecService {

    private final UserRepository userRepository;
    private final UserSpecRepository userSpecRepository;

    public UserMeResponse getMyProfile(Authentication authentication) {
        User user = getCurrentUser(authentication);

        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId())
                .orElse(null);

        return UserMeResponse.from(user, userSpec);
    }

    @Transactional
    public UserSpecResponse saveOrUpdateMySpec(
            Authentication authentication,
            UserSpecRequest request
    ) {
        User user = getCurrentUser(authentication);

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

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보가 없습니다."
            );
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사용자가 존재하지 않습니다."
                ));
    }
}