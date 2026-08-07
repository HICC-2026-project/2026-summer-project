package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 유저별 AI 커리어 로드맵 결과 캐시 테이블 (roadmap_caches).
 * 유저당 1건, 24시간 만료 구조.
 */
@Entity
@Table(name = "roadmap_caches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoadmapCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Gemini 로드맵 결과 전체 JSON (RoadmapResponse 직렬화 값) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb", nullable = false)
    private String resultJson;

    @UpdateTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "daily_update_count")
    @Builder.Default
    private Integer dailyUpdateCount = 0;

    @Column(name = "last_updated_date")
    private LocalDate lastUpdatedDate;
}
