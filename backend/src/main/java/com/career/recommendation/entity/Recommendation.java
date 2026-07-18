package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 유저별 AI 추천 결과 캐시 테이블 (recommendations).
 * 유저당 1건, 24시간 만료 구조.
 */
@Entity
@Table(name = "recommendations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Claude 추천 결과 전체 JSON (RecommendationResponse 직렬화 값) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb", nullable = false)
    private String resultJson;

    @UpdateTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 캐시가 아직 유효한지 확인 */
    public boolean isValid() {
        return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }
}
