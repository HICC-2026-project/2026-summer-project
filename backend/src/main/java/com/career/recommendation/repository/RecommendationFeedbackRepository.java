package com.career.recommendation.repository;

import com.career.recommendation.entity.RecommendationFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, UUID> {

    Optional<RecommendationFeedback> findByUser_IdAndActivity_Id(UUID userId, UUID activityId);

    /** GET /recommendations 응답에 myReaction을 실을 때 — 유저의 전체 반응을 한 번에 조회. */
    List<RecommendationFeedback> findByUser_Id(UUID userId);

    /**
     * PromptDataBuilder 프롬프트 주입용 — 유저가 남긴 반응 중 최근 갱신순 상한 개수까지만 조회한다.
     * reaction은 ReactionType.name()("LIKE"|"DISLIKE").
     */
    List<RecommendationFeedback> findByUser_IdAndReactionOrderByUpdatedAtDesc(
            UUID userId, String reaction, Pageable pageable);

    void deleteByUser_IdAndActivity_Id(UUID userId, UUID activityId);

    /** 관리자 집계(E10-2) — 활동별 LIKE/DISLIKE 수. dislike가 많은 순으로 정렬한다. */
    @Query("""
            SELECT f.activity.id AS activityId, f.activity.name AS activityName,
                   SUM(CASE WHEN f.reaction = 'LIKE' THEN 1L ELSE 0L END) AS likeCount,
                   SUM(CASE WHEN f.reaction = 'DISLIKE' THEN 1L ELSE 0L END) AS dislikeCount
            FROM RecommendationFeedback f
            GROUP BY f.activity.id, f.activity.name
            ORDER BY SUM(CASE WHEN f.reaction = 'DISLIKE' THEN 1L ELSE 0L END) DESC
            """)
    List<ActivityFeedbackSummaryRow> summarizeByActivity();

    interface ActivityFeedbackSummaryRow {
        UUID getActivityId();
        String getActivityName();
        Long getLikeCount();
        Long getDislikeCount();
    }
}
