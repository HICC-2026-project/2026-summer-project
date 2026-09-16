package com.career.recommendation.dto.job;

import com.career.recommendation.domain.JobType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobTypeResponse {
    private final String code;
    private final String label;

    public static JobTypeResponse from(JobType type) {
        return new JobTypeResponse(type.name(), type.getLabel());
    }
}
