package com.career.recommendation.repository;

import com.career.recommendation.entity.TargetJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TargetJobRepository extends JpaRepository<TargetJob, UUID> {

    /**
     * 함수 이름: findByUser_Id()
     * 기능: target_jobs 테이블에서 user_id가 일치하는 희망 직무 설정 1건을 조회한다.
     * 파라미터: UUID userId -> 희망 직무를 설정한 사용자의 ID
     * 반환값: 해당 사용자의 TargetJob (없으면 Optional.empty)
     */
    Optional<TargetJob> findByUser_Id(UUID userId);
}