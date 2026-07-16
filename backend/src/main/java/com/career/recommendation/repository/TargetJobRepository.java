package com.career.recommendation.repository;

import com.career.recommendation.entity.TargetJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TargetJobRepository extends JpaRepository<TargetJob, UUID> {

    Optional<TargetJob> findByUser_Id(UUID userId);
}