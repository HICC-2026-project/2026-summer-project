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
    /**
     * 함수 이름: findByIsActiveTrue()
     * 기능: activities 테이블에서 is_active가 true인 활동 전체를 조회한다.
     * 파라미터: 없음
     * 반환값: 활성 상태인 Activity 목록
     */
    List<Activity> findByIsActiveTrue();
    /**
     * 함수 이름: findByIsActiveTrue()
     * 기능: activities 테이블에서 is_active가 true인 활동을 페이지 단위로 조회한다.
     * 파라미터: Pageable pageable -> 페이지 번호·크기·정렬 정보
     * 반환값: 활성 상태인 Activity의 Page
     */
    Page<Activity> findByIsActiveTrue(Pageable pageable);
    /**
     * 함수 이름: findByTypeAndIsActiveTrue()
     * 기능: activities 테이블에서 type이 일치하고 is_active가 true인 활동을 조회한다.
     * 파라미터: String type -> 활동 유형 코드(예: INTERNSHIP)
     * 반환값: 해당 유형의 활성 Activity 목록
     */
    List<Activity> findByTypeAndIsActiveTrue(String type);
    /**
     * 함수 이름: findByTypeAndIsActiveTrue()
     * 기능: activities 테이블에서 type이 일치하고 is_active가 true인 활동을 페이지 단위로 조회한다.
     * 파라미터: String type -> 활동 유형 코드(예: INTERNSHIP)
     *           Pageable pageable -> 페이지 번호·크기·정렬 정보
     * 반환값: 해당 유형의 활성 Activity의 Page
     */
    Page<Activity> findByTypeAndIsActiveTrue(String type, Pageable pageable);

    /**
     * 함수 이름: findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc()
     * 기능: 로드맵 시기 매칭용 — activities 테이블에서 is_active가 true이고 마감일이 지정 기간(양끝 포함) 안에 있는 활동을
     *       마감일 오름차순으로 조회한다. RoadmapService에서 각 타임라인 단계에 실제 DB 활동을 매칭할 때 사용한다.
     * 파라미터: LocalDate deadlineStart -> 마감일 조회 구간의 시작일
     *           LocalDate deadlineEnd -> 마감일 조회 구간의 종료일
     * 반환값: 기간 내 마감되는 활성 Activity 목록(마감일 빠른 순)
     */
    List<Activity> findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc(
            LocalDate deadlineStart, LocalDate deadlineEnd);

    /**
     * 함수 이름: findRecommendableActivities()
     * 기능: 현재 신청 가능한 활동을 조회한다. activities 테이블에서 is_active가 true이고
     *       (마감일이 없거나 마감일이 today 이상)인 활동을 마감일 오름차순으로 정렬해 가져온다.
     *       마감일이 없는 활동은 상시 모집으로 간주하여 포함하고 마지막에 정렬한다.
     * 파라미터: LocalDate today -> 기준 날짜(이 날짜 이전에 마감된 활동은 제외)
     *           Pageable pageable -> 조회 개수 제한 등 페이지 정보
     * 반환값: 신청 가능한 Activity 목록
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
     * 함수 이름: searchOpenActivities()
     * 기능: 현재 신청 가능한 활동 목록 검색. activities 테이블에서 is_active가 true이고 (마감일 없음 또는 마감일 >= today)이며,
     *       유형 일치, 마감일 하한(deadlineAfter), 키워드(이름·주최·설명의 소문자 LIKE 검색), 태그 정규식 매칭 조건을
     *       모두 만족하는 활동을 페이지 단위로 조회한다. 마감일이 없는 활동은 상시 모집으로 간주하여 포함한다.
     *       필터는 전부 선택이며 "필터 없음"은 null이 아니라 빈 문자열/null 날짜로 넘긴다 —
     *       JPQL에서 null 파라미터는 DB 타입 추론이 실패할 수 있어 빈 문자열 비교가 안전하다.
     * 파라미터: LocalDate today -> 기준 날짜(이 날짜 이전에 마감된 활동은 제외)
     *           String type -> 활동 유형 코드(INTERNSHIP 등). "" = 전체
     *           LocalDate deadlineAfter -> 이 날짜 이후 마감(또는 상시)만. 조건 없음 = 아주 과거 날짜(null은 DB 타입 추론 실패)
     *           String keywordLike -> "%소문자 키워드%" 형태. "" = 조건 없음. 이름·주최·설명에서 찾는다
     *           String jobPattern -> 태그에 매칭할 POSIX 정규식(예: "백엔드|backend|서버"). "" = 조건 없음
     *           Pageable pageable -> 페이지 번호·크기·정렬 정보
     * 반환값: 조건을 만족하는 Activity의 Page
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

    /**
     * 함수 이름: deactivateExpired()
     * 기능: activities 테이블에서 is_active가 true이고 마감일이 today 이전인 행의 is_active를 false로 바꿔 비활성화한다
     *       (ActivityDeadlineScheduler). 마감일 당일은 유지한다. 실행 후 영속성 컨텍스트를 비운다(clearAutomatically).
     * 파라미터: LocalDate today -> 기준 날짜(이 날짜보다 마감일이 이른 활동이 대상)
     * 반환값: int -> 비활성화된 행 수
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Activity a SET a.isActive = false WHERE a.isActive = true AND a.deadline < :today")
    int deactivateExpired(@Param("today") LocalDate today);
}
