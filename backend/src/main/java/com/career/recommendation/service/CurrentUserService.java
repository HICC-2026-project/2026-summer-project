package com.career.recommendation.service;

import com.career.recommendation.entity.User;
import com.career.recommendation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보가 없습니다."
            );
        }

        UUID userId = extractUserId(authentication);

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사용자가 존재하지 않습니다."
                ));
    }

    private UUID extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof User user) {
            return user.getId();
        }

        UUID userIdFromPrincipal = extractUuidByGetter(principal, "getUserId");
        if (userIdFromPrincipal != null) {
            return userIdFromPrincipal;
        }

        UUID idFromPrincipal = extractUuidByGetter(principal, "getId");
        if (idFromPrincipal != null) {
            return idFromPrincipal;
        }

        try {
            return UUID.fromString(authentication.getName());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보에서 사용자 ID를 확인할 수 없습니다."
            );
        }
    }

    private UUID extractUuidByGetter(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);

            if (value instanceof UUID uuid) {
                return uuid;
            }

            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return UUID.fromString(stringValue);
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}