package com.career.recommendation.repository;

import com.career.recommendation.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByIsActiveTrue();
    Page<Activity> findByIsActiveTrue(Pageable pageable);
    List<Activity> findByTypeAndIsActiveTrue(String type);
    Page<Activity> findByTypeAndIsActiveTrue(String type, Pageable pageable);

    /**
     * 로드맵 시기 매칭용 — 지정 기간 내에 마감되는 활성 활동을 마감일순으로 조회.
     * RoadmapService에서 각 타임라인 단계에 실제 DB 활동을 매칭할 때 사용한다.
     */
    List<Activity> findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc(
            LocalDate deadlineStart, LocalDate deadlineEnd);
}
