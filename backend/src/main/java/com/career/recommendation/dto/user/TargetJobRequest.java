package com.career.recommendation.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TargetJobRequest {

    private String jobType;

    private String companySize;

    private String industry;
}