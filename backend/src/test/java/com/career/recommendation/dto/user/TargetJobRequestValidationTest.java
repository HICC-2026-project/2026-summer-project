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
}
