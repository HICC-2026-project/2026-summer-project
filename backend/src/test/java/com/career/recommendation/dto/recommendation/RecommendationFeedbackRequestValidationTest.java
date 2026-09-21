package com.career.recommendation.dto.recommendation;

import com.career.recommendation.domain.ReactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationFeedbackRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void LIKE는_허용한다() {
        RecommendationFeedbackRequest r = new RecommendationFeedbackRequest();
        r.setReaction(ReactionType.LIKE);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void DISLIKE는_허용한다() {
        RecommendationFeedbackRequest r = new RecommendationFeedbackRequest();
        r.setReaction(ReactionType.DISLIKE);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void reaction이_없으면_거절한다() {
        // 알 수 없는 문자열("NEUTRAL" 등)은 enum 역직렬화 단계에서 400으로 떨어지므로 여기선 null만 본다
        // (PasserReviewRequestValidationTest와 같은 관례).
        RecommendationFeedbackRequest r = new RecommendationFeedbackRequest();
        r.setReaction(null);
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
