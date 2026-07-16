package com.career.recommendation.repository;

import com.career.recommendation.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    Page<Activity> findByIsActiveTrue(Pageable pageable);
    List<Activity> findByTypeAndIsActiveTrue(String type);
    Page<Activity> findByTypeAndIsActiveTrue(String type, Pageable pageable);
}
