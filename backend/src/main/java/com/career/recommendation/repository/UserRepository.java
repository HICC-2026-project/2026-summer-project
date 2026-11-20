package com.career.recommendation.repository;

import com.career.recommendation.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * 함수 이름: findByEmail()
     * 기능: users 테이블에서 email이 일치하는 사용자 1명을 조회한다.
     * 파라미터: String email -> 조회할 이메일 주소
     * 반환값: 해당 User (없으면 Optional.empty)
     */
    Optional<User> findByEmail(String email);
    /**
     * 함수 이름: findByProviderAndProviderId()
     * 기능: users 테이블에서 소셜 로그인 제공자(provider)와 제공자 측 사용자 ID(providerId)가 모두 일치하는 사용자 1명을 조회한다.
     * 파라미터: String provider -> 로그인 제공자 이름(예: kakao)
     *           String providerId -> 제공자가 발급한 사용자 고유 ID
     * 반환값: 해당 User (없으면 Optional.empty)
     */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    /**
     * 함수 이름: existsByEmail()
     * 기능: users 테이블에 해당 email을 가진 사용자가 존재하는지 확인한다.
     * 파라미터: String email -> 확인할 이메일 주소
     * 반환값: 존재하면 true, 없으면 false
     */
    boolean existsByEmail(String email);
}
