package com.career.recommendation.dto.recommendation;

import com.career.recommendation.domain.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendationFeedbackRequest {

    /** 알 수 없는 값은 Jackson 역직렬화 단계에서 400(HttpMessageNotReadable)으로 떨어진다. */
    @NotNull(message = "reaction은 LIKE 또는 DISLIKE여야 합니다.")
    private ReactionType reaction;
}
