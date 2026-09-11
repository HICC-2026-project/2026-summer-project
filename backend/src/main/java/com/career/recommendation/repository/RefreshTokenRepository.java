package com.career.recommendation.repository;

import com.career.recommendation.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    /** 사용자의 리프레시 토큰 전부 폐기. 반환값은 폐기된 행 수(재사용 감지 로그용). */
    @Modifying
    int deleteByUserId(UUID userId);
}
