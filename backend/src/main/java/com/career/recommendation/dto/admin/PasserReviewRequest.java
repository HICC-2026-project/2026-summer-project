package com.career.recommendation.dto.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasserReviewRequest {

    @NotNull(message = "action은 필수입니다.")
    @Pattern(regexp = "APPROVE|REJECT", message = "action은 APPROVE 또는 REJECT여야 합니다.")
    private String action;

    @Size(max = 300, message = "반려 사유는 300자 이하여야 합니다.")
    private String reason;

    @AssertTrue(message = "반려할 때는 사유를 적어야 합니다.")
    @JsonIgnore
    public boolean isReasonPresentWhenRejecting() {
        if (!"REJECT".equals(action)) {
            return true;
        }
        return reason != null && !reason.isBlank();
    }
}
