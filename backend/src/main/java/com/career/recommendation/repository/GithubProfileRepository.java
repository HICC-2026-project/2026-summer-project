package com.career.recommendation.repository;

import com.career.recommendation.entity.GithubProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GithubProfileRepository extends JpaRepository<GithubProfile, UUID> {

    /**
     * 함수 이름: findByUser_Id()
     * 기능: github_profiles 테이블에서 user_id가 일치하는 GitHub 프로필 1건을 조회한다.
     * 파라미터: UUID userId -> GitHub 계정을 연동한 사용자의 ID
     * 반환값: 해당 사용자의 GithubProfile (없으면 Optional.empty)
     */
    Optional<GithubProfile> findByUser_Id(UUID userId);

    /**
     * 함수 이름: deleteByUser_Id()
     * 기능: github_profiles 테이블에서 user_id가 일치하는 GitHub 프로필 행을 삭제한다.
     * 파라미터: UUID userId -> 프로필을 삭제할 사용자의 ID
     * 반환값: 없음
     */
    void deleteByUser_Id(UUID userId);
}
