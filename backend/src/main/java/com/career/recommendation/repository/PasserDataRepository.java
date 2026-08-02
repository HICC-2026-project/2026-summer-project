package com.career.recommendation.repository;

import com.career.recommendation.entity.PasserData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PasserDataRepository extends JpaRepository<PasserData, UUID> {

    List<PasserData> findByActivityId(UUID activityId);

    List<PasserData> findByIsVerifiedTrue();

    /** 정규화 학점 비율만으로 유사 합격자 검색 (직무 데이터가 없을 때 폴백으로 사용) */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE p.isVerified = true
              AND p.gpa IS NOT NULL
              AND p.gpaMax IS NOT NULL
              AND p.gpaMax > 0
              AND (p.gpa / p.gpaMax) BETWEEN :minRatio AND :maxRatio
            """)
    List<PasserData> findSimilarByGpaRatio(
            @Param("minRatio") BigDecimal minRatio,
            @Param("maxRatio") BigDecimal maxRatio,
            Pageable pageable);

    /** 목표 직무 + 정규화 학점 비율로 유사 합격자 Top N 검색 */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE p.isVerified = true
              AND p.jobType = :jobType
              AND p.gpa IS NOT NULL
              AND p.gpaMax IS NOT NULL
              AND p.gpaMax > 0
              AND (p.gpa / p.gpaMax) BETWEEN :minRatio AND :maxRatio
            """)
    List<PasserData> findSimilarByJobTypeAndGpaRatio(
            @Param("jobType") String jobType,
            @Param("minRatio") BigDecimal minRatio,
            @Param("maxRatio") BigDecimal maxRatio,
            Pageable pageable);
}
