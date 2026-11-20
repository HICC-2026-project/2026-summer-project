package com.career.recommendation.dto.passer;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.entity.PasserData;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 본인 제보 상태 한 건. 스펙 상세(학점·어학 등)는 제보자 본인 것이라 내려줘도 되지만,
 * 이 화면의 목적은 "검수됐는지" 확인이라 식별에 필요한 최소만 싣는다.
 */
@Getter
@Builder
public class MyPasserReportResponse {

    private UUID reportId;
    private String jobType;
    private String jobTypeLabel;
    private Integer year;
    /** ReviewStatus.name() — PENDING | VERIFIED | REJECTED */
    private String status;
    private LocalDateTime createdAt;

    /**
     * 함수 이름: from()
     * 기능: PasserData 엔티티를 내 제보 목록용 응답으로 변환한다(직무 한글 라벨, 연도, 검수 상태, 생성 시각).
     * 파라미터: PasserData data -> 변환할 합격자 제보 엔티티
     * 반환값: MyPasserReportResponse -> 내 제보 응답 DTO
     */
    public static MyPasserReportResponse from(PasserData data) {
        return MyPasserReportResponse.builder()
                .reportId(data.getId())
                .jobType(data.getJobType())
                .jobTypeLabel(JobType.labelOf(data.getJobType()))
                .year(data.getYear())
                .status(data.reviewStatus().name())
                .createdAt(data.getCreatedAt())
                .build();
    }
}
