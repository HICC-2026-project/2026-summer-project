package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "passer_data")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasserData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    private Integer year;

    @Column(precision = 3, scale = 2)
    private BigDecimal gpa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_score", columnDefinition = "jsonb")
    private Map<String, Object> languageScore;

    @Column(columnDefinition = "text[]")
    private String[] certifications;

    @Column(name = "experience_count")
    @Builder.Default
    private Integer experienceCount = 0;

    @Column(name = "spec_summary", columnDefinition = "text")
    private String specSummary;            // 이름·학교 등 개인정보 저장 금지

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
