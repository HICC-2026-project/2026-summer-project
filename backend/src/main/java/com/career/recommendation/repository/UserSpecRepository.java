package com.career.recommendation.repository;

import com.career.recommendation.entity.UserSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSpecRepository extends JpaRepository<UserSpec, UUID> {

    /**
     * 함수 이름: findByUser_Id()
     * 기능: user_specs 테이블에서 user_id가 일치하는 사용자 스펙 1건을 조회한다.
     * 파라미터: UUID userId -> 스펙 소유 사용자의 ID
     * 반환값: 해당 사용자의 UserSpec (없으면 Optional.empty)
     */
    Optional<UserSpec> findByUser_Id(UUID userId);
}