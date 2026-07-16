package com.career.recommendation.service;

import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.InvalidTokenException;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserSpecRepository userSpecRepository;

    public UserMeResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("존재하지 않는 사용자입니다."));
        return UserMeResponse.of(user, userSpecRepository.findByUserId(userId).orElse(null));
    }
}
