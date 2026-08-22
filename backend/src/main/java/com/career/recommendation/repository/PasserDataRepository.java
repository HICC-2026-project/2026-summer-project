package com.career.recommendation.repository;

import com.career.recommendation.entity.PasserData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PasserDataRepository extends JpaRepository<PasserData, UUID> {

    List<PasserData> findByActivityId(UUID activityId);

    List<PasserData> findByIsVerifiedTrue();

    /**
     * 특정 직무의 비교 가능(검증 완료 또는 DEMO) 합격자 전원을 조회한다.
     * JobSpecProfileService가 직무 요구 프로필(분포·보유율)을 집계하는 데 쓴다.
     *
     * Top N이 아니라 전원을 보는 이유: 프로필은 사용자 스펙과 무관하게 데이터가 바뀔 때만
     * 변하는 집계값이어야 한다 — Top N에서 뽑으면 유저가 스펙을 조금만 고쳐도 결과가
     * 흔들려 위치·갭이 요청마다 요동친다.
     *
     * isVerified=false인 자가 제보 데이터는 제외한다 — 검증 안 된 데이터가 분포·보유율에
     * 섞이면 자가 제보를 통한 우회 게이밍이 가능해진다.
     */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
              AND p.jobType = :jobType
            """)
    List<PasserData> findAllComparableByJobType(@Param("jobType") String jobType);

    /** 직무 구분 없는 비교 가능 합격자 전원. 직무 표본 부족 시의 전체 프로필 폴백용. */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
            """)
    List<PasserData> findAllComparable();
}
