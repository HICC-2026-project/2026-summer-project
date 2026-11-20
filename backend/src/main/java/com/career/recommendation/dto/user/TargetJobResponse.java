package com.career.recommendation.dto.user;

import com.career.recommendation.entity.TargetJob;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TargetJobResponse {

    private UUID id;

    private UUID userId;

    private String jobType;

    private String companySize;

    private String industry;

    private LocalDateTime updatedAt;

    /**
     * 함수 이름: from()
     * 기능: TargetJob 엔티티를 응답 DTO로 변환한다(사용자 ID, 직무, 기업 규모, 산업, 수정 시각).
     * 파라미터: TargetJob targetJob -> 변환할 목표 직무 엔티티
     * 반환값: TargetJobResponse -> 목표 직무 응답 DTO
     */
    public static TargetJobResponse from(TargetJob targetJob) {
        return TargetJobResponse.builder()
                .id(targetJob.getId())
                .userId(targetJob.getUser().getId())
                .jobType(targetJob.getJobType())
                .companySize(targetJob.getCompanySize())
                .industry(targetJob.getIndustry())
                .updatedAt(targetJob.getUpdatedAt())
                .build();
    }
}