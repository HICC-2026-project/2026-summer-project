package com.career.recommendation.dto.admin;

import com.career.recommendation.domain.ReviewAction;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasserReviewRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 승인은_사유_없이_허용한다() {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction(ReviewAction.APPROVE);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void 반려는_사유가_없으면_거절한다() {
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction(ReviewAction.REJECT);
        r.setReason("   ");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void action이_없으면_거절한다() {
        // 알 수 없는 문자열("DELETE")은 enum 역직렬화 단계에서 400으로 떨어지므로 여기선 null만 본다.
        PasserReviewRequest r = new PasserReviewRequest();
        r.setAction(null);
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
