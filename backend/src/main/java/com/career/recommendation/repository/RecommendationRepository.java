package com.career.recommendation.repository;

import com.career.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    Optional<Recommendation> findByUser_Id(UUID userId);
}
