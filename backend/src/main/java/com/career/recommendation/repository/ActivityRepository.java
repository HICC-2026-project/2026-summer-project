package com.career.recommendation.repository;

import com.career.recommendation.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByIsActiveTrue();
    Page<Activity> findByIsActiveTrue(Pageable pageable);
    List<Activity> findByTypeAndIsActiveTrue(String type);
    Page<Activity> findByTypeAndIsActiveTrue(String type, Pageable pageable);

    /**
     * 로드맵 시기 매칭용 — 지정 기간 내에 마감되는 활성 활동을 마감일순으로 조회.
     * RoadmapService에서 각 타임라인 단계에 실제 DB 활동을 매칭할 때 사용한다.
     */
    List<Activity> findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc(
            LocalDate deadlineStart, LocalDate deadlineEnd);

    /**
     * 현재 신청 가능한 활동을 조회한다.
     * 마감일이 없는 활동은 상시 모집으로 간주하여 포함하고 마지막에 정렬한다.
     */
    @Query("""
            SELECT a
            FROM Activity a
            WHERE a.isActive = true
              AND (a.deadline IS NULL OR a.deadline >= :today)
            ORDER BY
              CASE WHEN a.deadline IS NULL THEN 1 ELSE 0 END,
              a.deadline ASC
            """)
    List<Activity> findRecommendableActivities(
            @Param("today") LocalDate today,
            Pageable pageable);

    /**
     * 현재 신청 가능한 활동 목록 검색. 마감일이 없는 활동은 상시 모집으로 간주하여 포함한다.
     * 필터는 전부 선택이며 "필터 없음"은 null이 아니라 빈 문자열/null 날짜로 넘긴다 —
     * JPQL에서 null 파라미터는 DB 타입 추론이 실패할 수 있어 빈 문자열 비교가 안전하다.
     *
     * @param type          활동 유형 코드(INTERNSHIP 등). "" = 전체
     * @param deadlineAfter 이 날짜 이후 마감(또는 상시)만. 조건 없음 = 아주 과거 날짜(null은 DB 타입 추론 실패)
     * @param keywordLike   "%소문자 키워드%" 형태. "" = 조건 없음. 이름·주최·설명에서 찾는다
     * @param jobPattern    태그에 매칭할 POSIX 정규식(예: "백엔드|backend|서버"). "" = 조건 없음
     */
    @Query("""
            SELECT a
            FROM Activity a
            WHERE a.isActive = true
              AND (a.deadline IS NULL OR a.deadline >= :today)
              AND (:type = '' OR a.type = :type)
              AND (a.deadline IS NULL OR a.deadline >= :deadlineAfter)
              AND (:keywordLike = ''
                   OR lower(a.name) LIKE :keywordLike ESCAPE '!'
                   OR lower(coalesce(a.organization, '')) LIKE :keywordLike ESCAPE '!'
                   OR lower(coalesce(a.description, '')) LIKE :keywordLike ESCAPE '!')
              AND (:jobPattern = ''
                   OR function('texticregexeq', function('array_to_string', a.tags, ' '), :jobPattern) = true)
            """)
    Page<Activity> searchOpenActivities(
            @Param("today") LocalDate today,
            @Param("type") String type,
            @Param("deadlineAfter") LocalDate deadlineAfter,
            @Param("keywordLike") String keywordLike,
            @Param("jobPattern") String jobPattern,
            Pageable pageable);

    /** 마감일이 today 이전인 활성 활동을 비활성화한다(ActivityDeadlineScheduler). 마감일 당일은 유지. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Activity a SET a.isActive = false WHERE a.isActive = true AND a.deadline < :today")
    int deactivateExpired(@Param("today") LocalDate today);
}
