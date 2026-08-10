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
 * 유저당 1건. 스펙 변경 시에만 재생성하며 하루 갱신 횟수를 3회로 제한한다.
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

    /**
     * ⚠️ 이름은 createdAt이지만 @UpdateTimestamp라서 실제로는 "마지막 캐시 갱신 시각"이다.
     * 생성 시각이 아니다. 캐시 저장(save)이 일어날 때마다 현재 시각으로 덮어써진다.
     *
     * 스펙 변경 여부 판정(isSpecModifiedSince)이 이 값을 기준으로 동작한다.
     * "userSpec.updatedAt이 마지막 갱신 시각보다 뒤면 스펙이 바뀐 것"이라는 의미이므로,
     * 이 필드를 @CreationTimestamp로 바꾸면 한 번 스펙을 수정한 뒤로는
     * 매 요청마다 스펙이 바뀐 것으로 판정되어 Gemini를 계속 호출하게 된다.
     */
    @UpdateTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "daily_update_count")
    @Builder.Default
    private Integer dailyUpdateCount = 0;

    @Column(name = "last_updated_date")
    private LocalDate lastUpdatedDate;
}
