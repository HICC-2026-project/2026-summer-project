package com.career.recommendation.entity;

import com.career.recommendation.domain.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "passer_data")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasserData {

    /** data_origin 값. 사용자 제보 — 검수 대상. */
    public static final String ORIGIN_USER_REPORT = "USER_REPORT";
    /** 발표·검증용 합성 데이터 — 검수 없이 비교 가능 집합에 포함(PasserDataRepository 참고). */
    public static final String ORIGIN_DEMO = "DEMO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    /**
     * 제보자. USER_REPORT에만 있고 DEMO·PUBLIC_REVIEW·탈퇴 사용자 제보는 null.
     * 본인 제보 조회·중복 제한용 내부 정보 — 비교·추천 응답 DTO에 절대 싣지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporter;

    @Column(name = "job_type")
    private String jobType;   // JobType enum의 name() — 정의는 domain.JobType 한 곳에서만

    private Integer year;

    @Column(precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(name = "gpa_max", precision = 4, scale = 2)
    private BigDecimal gpaMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_scores", columnDefinition = "jsonb")
    private List<Map<String, Object>> languageScores;

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

    /** DEMO | PUBLIC_REVIEW | USER_REPORT | UNKNOWN */
    @Column(name = "data_origin", nullable = false, length = 20)
    @Builder.Default
    private String dataOrigin = "UNKNOWN";

    @Column(name = "proof_original_name", length = 255)
    private String proofOriginalName;

    /** 로컬 저장소 안에서 사용하는 UUID 기반 파일명. 외부에 직접 노출하지 않는다. */
    @Column(name = "proof_stored_name", length = 100)
    private String proofStoredName;

    @Column(name = "proof_content_type", length = 50)
    private String proofContentType;

    @Column(name = "proof_file_size")
    private Long proofFileSize;

    // --- E11-5(V29) — 합격자 측 동의 기반 GitHub 파생값. 아이디·레포명·URL은 저장하지 않는다. ---

    /** 기여 영역(ExperienceArea 코드) 상위 목록. GitHub 아이디 제보·동의가 없으면 null. */
    @Column(columnDefinition = "text[]")
    private String[] areas;

    /** 사용 스택(라이브러리명) 상위 목록. GitHub 아이디 제보·동의가 없으면 null. */
    @Column(columnDefinition = "text[]")
    private String[] stack;

    /**
     * GitHub 분석 집계값만 담는다: {repos, commits, activeMonths, jobRatios}. 레포명·URL·
     * 커밋 메시지 등 식별 가능한 원본은 절대 담지 않는다(PasserGithubContributionRunner 참고).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "github_derived", columnDefinition = "jsonb")
    private Map<String, Object> githubDerived;

    // --- 검수 이력 (V21) ---
    /** 검수 시각. null이면 아직 검수 전(PENDING). 승인·반려 모두 채운다. */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    /** 반려 사유. 승인이면 null. */
    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    /** 검수 상태 — isVerified와 reviewedAt 두 컬럼에서 파생한다. 세 값의 단일 정의는 domain.ReviewStatus. */
    public ReviewStatus reviewStatus() {
        if (Boolean.TRUE.equals(isVerified)) return ReviewStatus.VERIFIED;
        if (reviewedAt != null) return ReviewStatus.REJECTED;
        return ReviewStatus.PENDING;
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
