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