package com.career.recommendation.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.career.recommendation.validation.ValidJobType;
import lombok.Getter;
import lombok.Setter;

/**
 * companySize·industry는 DB 컬럼 폭(둘 다 VARCHAR(50) — company_size는 V11이 넓히려다 IF NOT EXISTS에
 * 막혀 20으로 남아 있던 것을 V25에서 실제로 넓혔다)을 넘으면 DataIntegrityViolationException으로 500이
 * 났다. @Size로 먼저 막아 400(VALIDATION_ERROR)으로 돌려준다.
 */
@Getter
@Setter
public class TargetJobRequest {

    @NotBlank(message = "목표 직무는 필수입니다.")
    @ValidJobType(message = "지원하지 않는 목표 직무입니다.")
    private String jobType;

    @Size(max = 50, message = "companySize는 50자를 넘을 수 없습니다.")
    private String companySize;

    @Size(max = 50, message = "industry는 50자를 넘을 수 없습니다.")
    private String industry;
}
