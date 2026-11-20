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

    /**
     * 함수 이름: isReasonPresentWhenRejecting()
     * 기능: 반려(REJECT)일 때 사유가 비어 있지 않은지 검증한다. action이 REJECT가 아니면 항상 true,
     *       REJECT이면 reason이 null이 아니고 공백이 아닐 때만 true이다(false이면 검증 실패).
     * 파라미터: 없음
     * 반환값: boolean -> 검증 통과 여부(true: 통과, false: 반려인데 사유 없음)
     */
    @AssertTrue(message = "반려할 때는 사유를 적어야 합니다.")
    @JsonIgnore
    public boolean isReasonPresentWhenRejecting() {
        if (action != ReviewAction.REJECT) {
            return true;
        }
        return reason != null && !reason.isBlank();
    }
}
