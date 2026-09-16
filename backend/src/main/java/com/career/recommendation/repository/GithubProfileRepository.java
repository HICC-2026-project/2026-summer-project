package com.career.recommendation.repository;

import com.career.recommendation.entity.GithubProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GithubProfileRepository extends JpaRepository<GithubProfile, UUID> {

    Optional<GithubProfile> findByUser_Id(UUID userId);

    void deleteByUser_Id(UUID userId);
}
