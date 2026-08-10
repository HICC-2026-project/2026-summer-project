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
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
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
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
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

    /** 목표 직무에서 정규화 학점 비율 차이가 가장 작은 합격자 Top N 검색 (범위 이탈 시 폴백) */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
              AND p.jobType = :jobType
              AND p.gpa IS NOT NULL
              AND p.gpaMax IS NOT NULL
              AND p.gpaMax > 0
            ORDER BY ABS((p.gpa / p.gpaMax) - :userRatio) ASC
            """)
    List<PasserData> findClosestByJobTypeAndGpaRatio(
            @Param("jobType") String jobType,
            @Param("userRatio") BigDecimal userRatio,
            Pageable pageable);

    /** 전체 합격자 중 정규화 학점 비율 차이가 가장 작은 합격자 Top N 검색 (범위 이탈 시 폴백) */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
              AND p.gpa IS NOT NULL
              AND p.gpaMax IS NOT NULL
              AND p.gpaMax > 0
            ORDER BY ABS((p.gpa / p.gpaMax) - :userRatio) ASC
            """)
    List<PasserData> findClosestByGpaRatio(
            @Param("userRatio") BigDecimal userRatio,
            Pageable pageable);

    /**
     * 검증된(또는 DEMO) 합격자 전원의 자격증 배열을 직무 구분 없이 전부 조회한다.
     * MatchScoreCalculator가 "표에 없어도 합격자 DB에 실제 등장하면 인정" 판정에 쓴다.
     * 비교 대상 Top N(유사 합격자)이 아니라 테이블 전체를 봐야, 유사 합격자 구성이
     * 바뀔 때마다(스펙 미세 수정·데이터 추가 등) 같은 자격증의 인식 여부가 흔들리지 않는다.
     * isVerified=false인 자가 제보 데이터는 제외한다 — 검증 안 된 자격증으로
     * "실재 인정"을 만들 수 있으면 자가 제보를 통한 우회 게이밍이 가능해진다.
     */
    @Query("""
            SELECT p.certifications
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
              AND p.certifications IS NOT NULL
            """)
    List<String[]> findAllVerifiedCertificationArrays();
}
