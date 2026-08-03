package com.career.recommendation.repository;

import com.career.recommendation.entity.RoadmapCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoadmapCacheRepository extends JpaRepository<RoadmapCache, UUID> {
    Optional<RoadmapCache> findByUser_Id(UUID userId);
}
