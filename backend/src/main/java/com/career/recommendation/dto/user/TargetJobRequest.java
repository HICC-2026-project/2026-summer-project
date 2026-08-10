package com.career.recommendation.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ⚠️ companySize·industry는 검증이 전혀 없었다. DB 컬럼 폭(V3: company_size VARCHAR(20),
 * industry VARCHAR(50) — V11이 company_size를 VARCHAR(50)으로 넓히려 했지만
 * "ADD COLUMN IF NOT EXISTS"라서 이미 있던 V3의 VARCHAR(20) 컬럼은 그대로 남았다. 실제
 * 스키마는 여전히 VARCHAR(20)이다)를 넘는 값을 보내면 DataIntegrityViolationException이
 * 컨트롤러까지 전파돼 500이 났다. @Size로 사전에 막아 400(VALIDATION_ERROR)으로 바뀌게
 * 한다 — 컬럼을 실제로 넓히는 마이그레이션은 별도로 필요하다.
 */
@Getter
@Setter
public class TargetJobRequest {

    @NotBlank(message = "목표 직무는 필수입니다.")
    @Pattern(
            regexp = "BACKEND|FRONTEND|AI_ML|DATA_ENGINEER|PM|SECURITY",
            message = "지원하지 않는 목표 직무입니다."
    )
    private String jobType;

    @Size(max = 20, message = "companySize는 20자를 넘을 수 없습니다.")
    private String companySize;

    @Size(max = 50, message = "industry는 50자를 넘을 수 없습니다.")
    private String industry;
}
