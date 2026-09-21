package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * E10-2(F-09) — 활동에 대한 사용자 반응(LIKE/DISLIKE). 사용자·활동 조합당 1행(UNIQUE).
 * 다시 반응을 남기면 이 행을 갱신(upsert)하고, 같은 반응을 재클릭하면 행을 삭제해 해제한다.
 */
@Entity
@Table(name = "recommendation_feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** ReactionType.name(). */
    @Column(nullable = false, length = 10)
    private String reaction;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
