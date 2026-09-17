package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E3(1단계) — 사용자가 등록한 GitHub 공개 레포 분석 결과. 사용자당 1행(user_id UNIQUE),
 * 재분석 시 이 행을 갱신한다(status·failure_reason·job_ratios·repos·commit_total·
 * active_months·analyzed_at). requested_at은 매 분석 요청마다 갱신해 쿨다운(24h) 기준으로 쓴다.
 */
@Entity
@Table(name = "github_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String username;

    /** GithubProfileStatus.name(). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    /** [{"jobType":"BACKEND","ratio":0.62}, ...] — OTHER 포함, 합 1.0. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_ratios", columnDefinition = "jsonb")
    private List<Map<String, Object>> jobRatios;

    /** [{name, primaryJob, commits, files, firstCommitAt, lastCommitAt, mainLanguage}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> repos;

    @Column(name = "commit_total")
    private Integer commitTotal;

    @Column(name = "active_months")
    private Integer activeMonths;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
