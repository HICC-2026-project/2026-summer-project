package com.career.recommendation.repository;

import com.career.recommendation.entity.UserSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSpecRepository extends JpaRepository<UserSpec, UUID> {

    Optional<UserSpec> findByUser_Id(UUID userId);
}