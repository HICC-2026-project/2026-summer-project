package com.career.recommendation.dto.admin;

import com.career.recommendation.domain.ReviewAction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasserReviewRequest {

    /** 알 수 없는 값은 Jackson 역직렬화 단계에서 400(HttpMessageNotReadable)으로 떨어진다. */
    @NotNull(message = "action은 APPROVE 또는 REJECT여야 합니다.")
    private ReviewAction action;

    @Size(max = 300, message = "반려 사유는 300자 이하여야 합니다.")
    private String reason;

    @AssertTrue(message = "반려할 때는 사유를 적어야 합니다.")
    @JsonIgnore
    public boolean isReasonPresentWhenRejecting() {
        if (action != ReviewAction.REJECT) {
            return true;
        }
        return reason != null && !reason.isBlank();
    }
}
