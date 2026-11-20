package com.career.recommendation.repository;

import com.career.recommendation.entity.RoadmapCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoadmapCacheRepository extends JpaRepository<RoadmapCache, UUID> {
    /**
     * 함수 이름: findByUser_Id()
     * 기능: roadmap_caches 테이블에서 user_id가 일치하는 로드맵 캐시 1건을 조회한다.
     * 파라미터: UUID userId -> 로드맵 캐시 소유 사용자의 ID
     * 반환값: 해당 사용자의 RoadmapCache (없으면 Optional.empty)
     */
    Optional<RoadmapCache> findByUser_Id(UUID userId);
}
