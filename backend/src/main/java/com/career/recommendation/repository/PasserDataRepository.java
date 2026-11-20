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

    /**
     * 함수 이름: findByActivityId()
     * 기능: passer_data 테이블에서 activity_id가 일치하는 합격자 데이터를 조회한다.
     * 파라미터: UUID activityId -> 대상 활동의 ID
     * 반환값: 해당 활동에 연결된 PasserData 목록
     */
    List<PasserData> findByActivityId(UUID activityId);

    /**
     * 함수 이름: findByIsVerifiedTrue()
     * 기능: passer_data 테이블에서 is_verified가 true인(검증 완료) 합격자 데이터를 전부 조회한다.
     * 파라미터: 없음
     * 반환값: 검증된 PasserData 목록
     */
    List<PasserData> findByIsVerifiedTrue();

    /**
     * 함수 이름: findAllByReporter_IdOrderByCreatedAtDesc()
     * 기능: 본인이 제보한 합격자 데이터를 passer_data 테이블에서 reporter_id로 찾아 생성일시 최신순으로 조회한다.
     *       제보 상태 확인용.
     * 파라미터: UUID reporterId -> 제보한 사용자의 ID
     * 반환값: 해당 사용자가 제보한 PasserData 목록(최신순)
     */
    List<PasserData> findAllByReporter_IdOrderByCreatedAtDesc(UUID reporterId);

    /**
     * 함수 이름: existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull()
     * 기능: 같은 사용자가 같은 직무·연도로 아직 검수되지 않은(reviewed_at이 null인) 제보를 이미 올렸는지
     *       passer_data 테이블에서 확인한다(중복 제보 차단).
     * 파라미터: UUID reporterId -> 제보한 사용자의 ID
     *           String jobType -> 직무 구분 값
     *           Integer year -> 합격 연도
     * 반환값: 미검수 중복 제보가 있으면 true, 없으면 false
     */
    boolean existsByReporter_IdAndJobTypeAndYearAndReviewedAtIsNull(UUID reporterId, String jobType, Integer year);

    /**
     * 함수 이름: countByReporter_IdAndCreatedAtAfter()
     * 기능: passer_data 테이블에서 해당 사용자가 지정 시각 이후에 생성한 제보 수를 센다(일일 제보 상한 판정용).
     * 파라미터: UUID reporterId -> 제보한 사용자의 ID
     *           LocalDateTime after -> 이 시각 이후(초과)에 생성된 제보만 집계
     * 반환값: long -> 조건에 맞는 제보 수
     */
    long countByReporter_IdAndCreatedAtAfter(UUID reporterId, LocalDateTime after);

    /**
     * 함수 이름: findReportsByStatus()
     * 기능: 검수(관리자) 목록. passer_data 테이블에서 data_origin이 USER_REPORT인 사용자 제보만 대상으로
     *       상태별로 조회하고 검수일(없으면 생성일) 최신순으로 정렬한다 — DEMO·PUBLIC_REVIEW는 검수 개념이 없다.
     *       상태는 PasserData.reviewStatus()와 같은 규칙으로 두 컬럼에서 판정한다:
     *       PENDING = 미검수, VERIFIED = 승인, REJECTED = 검수했지만 미승인. 최신 건이 먼저.
     * 파라미터: String status -> 검수 상태(PENDING, VERIFIED, REJECTED 중 하나)
     *           Pageable pageable -> 페이지 번호·크기 정보
     * 반환값: 해당 상태의 제보 PasserData의 Page
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
     * 함수 이름: findReviewedWithProofBefore()
     * 기능: 증빙 보관 기한이 지난 제보를 조회한다. passer_data 테이블에서 증빙 파일(proofStoredName)이 있고
     *       검수일(reviewedAt)이 기준 시각 이전인 행이 대상이다. 검수가 끝난(승인·반려) 제보의 증빙은 더 볼 일이 없으므로
     *       reviewedAt 기준 일정 기간 뒤 파일을 지운다(ProofRetentionScheduler). 미검수(PENDING)는 대상이 아니다.
     * 파라미터: LocalDateTime before -> 이 시각보다 이전에 검수된 제보만 조회
     * 반환값: 증빙 보관 기한이 지난 PasserData 목록
     */
    @Query("""
            SELECT p FROM PasserData p
            WHERE p.proofStoredName IS NOT NULL
              AND p.reviewedAt IS NOT NULL
              AND p.reviewedAt < :before
            """)
    List<PasserData> findReviewedWithProofBefore(@Param("before") LocalDateTime before);

    /**
     * 함수 이름: countComparableByJobType()
     * 기능: 운영 요약용. passer_data 테이블에서 비교 가능(검증 완료 또는 DEMO)한 합격자를 직무(jobType)별로 묶어 인원 수를 센다.
     * 파라미터: 없음
     * 반환값: Object[] 목록 -> 각 원소는 [직무(String), 합격자 수(Long)]
     */
    @Query("""
            SELECT p.jobType, COUNT(p)
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
            GROUP BY p.jobType
            """)
    List<Object[]> countComparableByJobType();

    /**
     * 함수 이름: countPendingReports()
     * 기능: 운영 요약용. passer_data 테이블에서 data_origin이 USER_REPORT이고 아직 검수되지 않은(reviewedAt이 null) 제보 건수를 센다.
     * 파라미터: 없음
     * 반환값: long -> 검수 대기 건수
     */
    @Query("SELECT COUNT(p) FROM PasserData p WHERE p.dataOrigin = 'USER_REPORT' AND p.reviewedAt IS NULL")
    long countPendingReports();

    /**
     * 함수 이름: findAllComparableByJobType()
     * 기능: passer_data 테이블에서 특정 직무(jobType)의 비교 가능(검증 완료 또는 DEMO) 합격자 전원을 조회한다.
     *       JobSpecProfileService가 직무 요구 프로필(분포·보유율)을 집계하는 데 쓴다.
     * 파라미터: String jobType -> 조회할 직무 구분 값
     * 반환값: 해당 직무의 비교 가능 PasserData 목록
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

    /**
     * 함수 이름: findAllComparable()
     * 기능: passer_data 테이블에서 직무 구분 없이 비교 가능(검증 완료 또는 DEMO)한 합격자 전원을 조회한다.
     *       직무 표본 부족 시의 전체 프로필 폴백용.
     * 파라미터: 없음
     * 반환값: 비교 가능한 PasserData 전체 목록
     */
    @Query("""
            SELECT p
            FROM PasserData p
            WHERE (p.isVerified = true OR p.dataOrigin = 'DEMO')
            """)
    List<PasserData> findAllComparable();
}
