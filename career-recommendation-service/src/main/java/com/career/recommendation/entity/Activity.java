package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String type;           // INTERNSHIP | EXTERNAL | COMPETITION

    @Column(nullable = false)
    private String name;

    private String organization;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDate deadline;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // {"min_gpa":3.0,"required_certs":["정보처리기사"]}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_spec", columnDefinition = "jsonb")
    private Map<String, Object> targetSpec;

    @Column(columnDefinition = "text[]")
    private String[] tags;

    private String url;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
