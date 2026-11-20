package com.career.recommendation.dto.admin;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.entity.PasserData;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 검수 화면용 제보 한 건. 검수자가 증빙과 대조할 수 있게 스펙 전체를 싣되,
 * 제보자 식별자(reporter)는 싣지 않는다 — 검수는 "합격 사실·스펙이 증빙과 맞는가"만 보면 되고,
 * 누가 제보했는지는 판단에 필요 없다.
 */
@Getter
@Builder
public class AdminPasserReportResponse {

    private UUID reportId;
    private String jobType;
    private String jobTypeLabel;
    private Integer year;
    private BigDecimal gpa;
    private BigDecimal gpaMax;
    private List<Map<String, Object>> languageScores;
    private List<String> certifications;
    private Integer experienceCount;
    /** ReviewStatus.name() */
    private String status;
    private Proof proof;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String rejectReason;

    @Getter
    @Builder
    public static class Proof {
        private String originalName;
        private String contentType;
        private Long fileSize;
    }

    /**
     * 함수 이름: from()
     * 기능: PasserData 엔티티를 관리자용 합격자 리포트 응답으로 변환한다. 직무 한글 라벨을 채우고, null인 어학점수·자격증은
     *       빈 리스트로 바꾸며, 증빙 파일이 저장돼 있을 때만 proof(원본 파일명·형식·크기)를 채운다.
     * 파라미터: PasserData p -> 변환할 합격자 엔티티
     * 반환값: AdminPasserReportResponse -> 관리자 화면용 리포트 응답 DTO
     */
    public static AdminPasserReportResponse from(PasserData p) {
        return AdminPasserReportResponse.builder()
                .reportId(p.getId())
                .jobType(p.getJobType())
                .jobTypeLabel(JobType.labelOf(p.getJobType()))
                .year(p.getYear())
                .gpa(p.getGpa())
                .gpaMax(p.getGpaMax())
                .languageScores(p.getLanguageScores() != null ? p.getLanguageScores() : List.of())
                .certifications(p.getCertifications() != null ? Arrays.asList(p.getCertifications()) : List.of())
                .experienceCount(p.getExperienceCount())
                .status(p.reviewStatus().name())
                .proof(p.getProofStoredName() == null ? null : Proof.builder()
                        .originalName(p.getProofOriginalName())
                        .contentType(p.getProofContentType())
                        .fileSize(p.getProofFileSize())
                        .build())
                .createdAt(p.getCreatedAt())
                .reviewedAt(p.getReviewedAt())
                .rejectReason(p.getRejectReason())
                .build();
    }
}
