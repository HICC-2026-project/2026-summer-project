package com.career.recommendation.repository;

import com.career.recommendation.entity.PasserData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PasserDataRepository extends JpaRepository<PasserData, UUID> {

    List<PasserData> findByActivityId(UUID activityId);

    List<PasserData> findByIsVerifiedTrue();

    /** 본인이 제보한 합격자 데이터(최신순). 제보 상태 확인용. */
    List<PasserData> findAllByReporter_IdOrderByCreatedAtDesc(UUID reporterId);

    /** 같은 사용자가 같은 직무·연도로 아직 검수되지 않은 제보를 이미 올렸는지 (중복 제보 차단). */
    boolean existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull(UUID reporterId, String jobType, Integer year);

    /** 사용자의 일정 시각 이후 제보 수 (일일 제보 상한). */
    long countByReporter_IdAndCreatedAtAfter(UUID reporterId, LocalDateTime after);

    /**
     * 검수(관리자) 목록. 사용자 제보(USER_REPORT)만 대상이다 — DEMO·PUBLIC_REVIEW는 검수 개념이 없다.
     * 상태는 PasserData.reviewStatus()와 같은 규칙으로 두 컬럼에서 판정한다:
     * PENDING = 미검수, VERIFIED = 승인, REJECTED = 검수했지만 미승인. 최신 건이 먼저.
     */
    @Query("""
            SELECT p FROM PasserData p
            WHERE p.dataOrigin = 'USER_REPORT'
              AND (
                   (:status = 'PENDING'  AND p.reviewedAt IS NULL)
                OR (:status = 'VERIFIED' AND p.isVerified = true)
                OR (:status = 'REJECTED' AND p.isVerified = false AND p.reviewedAt IS NOT NULL)
              )
            ORDER BY COALESCE(p.reviewedAt, p.createdAt) DESC
            """)
    Page<PasserData> findReportsByStatus(@Param("status") String status, Pageable pageable);

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
