package com.career.recommendation.repository;

import com.career.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    /**
     * 함수 이름: findByUser_Id()
     * 기능: recommendations 테이블에서 user_id가 일치하는 추천 결과 1건을 조회한다.
     * 파라미터: UUID userId -> 추천 결과 소유 사용자의 ID
     * 반환값: 해당 사용자의 Recommendation (없으면 Optional.empty)
     */
    Optional<Recommendation> findByUser_Id(UUID userId);
}
