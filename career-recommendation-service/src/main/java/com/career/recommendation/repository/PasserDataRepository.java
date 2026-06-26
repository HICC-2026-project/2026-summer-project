package com.career.recommendation.repository;

import com.career.recommendation.entity.PasserData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PasserDataRepository extends JpaRepository<PasserData, UUID> {
    List<PasserData> findByActivityId(UUID activityId);
    List<PasserData> findByIsVerifiedTrue();

    // 학점 범위로 유사 합격자 검색 (BE-1에서 사용)
    @Query("SELECT p FROM PasserData p WHERE p.gpa BETWEEN :minGpa AND :maxGpa AND p.isVerified = true")
    List<PasserData> findSimilarByGpa(@Param("minGpa") BigDecimal minGpa,
                                       @Param("maxGpa") BigDecimal maxGpa);
}
