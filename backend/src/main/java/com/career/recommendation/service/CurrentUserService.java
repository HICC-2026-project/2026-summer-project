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

    /**
     * 함수 이름: getCurrentUser()
     * 기능: 인증 정보에서 사용자 ID를 꺼내 DB에서 User 엔티티를 조회한다.
     * 파라미터: Authentication authentication -> 현재 요청의 인증 정보
     * 반환값: 현재 로그인한 User 엔티티
     * 예외: ResponseStatusException -> 인증 정보가 없으면 401, 사용자가 DB에 없으면 404
     */
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

    /**
     * 함수 이름: extractUserId()
     * 기능: 인증 정보의 principal에서 사용자 UUID를 찾는다. principal이 User면 바로 ID를 쓰고,
     *       아니면 getUserId → getId 게터 순서로 시도하고, 마지막으로 authentication 이름을 UUID로 해석한다.
     * 파라미터: Authentication authentication -> 현재 요청의 인증 정보
     * 반환값: 사용자 UUID
     * 예외: ResponseStatusException -> 어떤 방법으로도 ID를 알 수 없으면 401
     */
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

    /**
     * 함수 이름: extractUuidByGetter()
     * 기능: 리플렉션으로 대상 객체의 게터를 호출해 UUID를 얻는다. 반환값이 UUID거나 비어 있지 않은
     *       문자열이면 UUID로 변환하고, 게터가 없거나 호출에 실패하면 예외 없이 null을 돌려준다.
     * 파라미터: Object target -> 게터를 호출할 객체(principal)
     *           String methodName -> 호출할 게터 이름(예: "getUserId")
     * 반환값: 찾은 UUID, 찾지 못하면 null
     */
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