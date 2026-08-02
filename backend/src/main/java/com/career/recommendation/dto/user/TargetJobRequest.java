package com.career.recommendation.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TargetJobRequest {

    @NotBlank(message = "목표 직무는 필수입니다.")
    @Pattern(
            regexp = "BACKEND|FRONTEND|AI_ML|DATA_ENGINEER|PM|SECURITY",
            message = "지원하지 않는 목표 직무입니다."
    )
    private String jobType;

    private String companySize;

    private String industry;
}
