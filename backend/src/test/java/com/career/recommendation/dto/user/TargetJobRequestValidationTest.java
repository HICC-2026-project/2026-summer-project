package com.career.recommendation.dto.user;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetJobRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 표준_직무코드는_허용한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 화면_표시용_직무명은_거절한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("SW 개발");

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void companySize가_DB_컬럼_폭_20자를_넘으면_거절한다() {
        // 실제 컬럼은 VARCHAR(20)이다(V11이 넓히려 했지만 IF NOT EXISTS라서 V3의 컬럼이
        // 그대로 남았다). 검증 없이 통과시키면 DataIntegrityViolationException으로 500이 났다.
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setCompanySize("가".repeat(21));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void companySize가_20자_이하면_허용한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setCompanySize("가".repeat(20));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void industry가_DB_컬럼_폭_50자를_넘으면_거절한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setIndustry("가".repeat(51));

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
