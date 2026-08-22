package com.career.recommendation.dto.admin;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasserReviewRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 승인은_사유_없이_허용한다() {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction("APPROVE");
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void 반려는_사유가_없으면_거절한다() {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction("REJECT");
        r.setReason("   ");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void 알_수_없는_action은_거절한다() {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction("DELETE");
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
