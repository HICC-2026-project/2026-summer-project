package com.career.recommendation.repository;

import com.career.recommendation.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    /**
     * 함수 이름: findByToken()
     * 기능: refresh_tokens 테이블에서 token 값이 일치하는 리프레시 토큰 1건을 조회한다.
     * 파라미터: String token -> 클라이언트가 제시한 리프레시 토큰 문자열
     * 반환값: 해당 RefreshToken (없으면 Optional.empty)
     */
    Optional<RefreshToken> findByToken(String token);
    /**
     * 함수 이름: deleteByUserId()
     * 기능: refresh_tokens 테이블에서 해당 사용자의 리프레시 토큰을 전부 삭제(폐기)한다.
     *       반환값은 폐기된 행 수(재사용 감지 로그용).
     * 파라미터: UUID userId -> 토큰을 폐기할 사용자의 ID
     * 반환값: int -> 삭제된 행 수
     */
    @Modifying
    int deleteByUserId(UUID userId);
}
