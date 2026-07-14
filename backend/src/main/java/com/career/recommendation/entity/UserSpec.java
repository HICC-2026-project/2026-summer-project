package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_specs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(precision = 3, scale = 2)
    private BigDecimal gpa;

    @Column(name = "gpa_max", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal gpaMax = new BigDecimal("4.5");

    // {"TOEIC":"850","OPIc":"IM2"} — one entry per language test type the user filled in
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_scores", columnDefinition = "jsonb")
    private Map<String, String> languageScores;

    @Column(columnDefinition = "text[]")
    private String[] certifications;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
