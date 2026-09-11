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
    void companySize가_DB_컬럼_폭_50자를_넘으면_거절한다() {
        // 컬럼은 VARCHAR(50)(V25). 검증 없이 통과시키면 DataIntegrityViolationException으로 500이 났다.
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setCompanySize("가".repeat(51));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void companySize가_50자_이하면_허용한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setCompanySize("가".repeat(50));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void industry가_DB_컬럼_폭_50자를_넘으면_거절한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BACKEND");
        request.setIndustry("가".repeat(51));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void 소문자_직무코드는_허용한다_서비스가_정규화한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("backend");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 레거시_별칭은_거절한다() {
        TargetJobRequest request = new TargetJobRequest();
        request.setJobType("BE");

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
