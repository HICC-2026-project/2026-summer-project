package com.career.recommendation.dto.experience;

import com.career.recommendation.dto.user.ExperienceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** E11-6 — POST /api/v1/users/me/experiences/enrich 요청 바디. */
@Getter
@Setter
public class ExperienceEnrichRequest {

    @NotNull(message = "경험 정보는 필수입니다.")
    @Valid
    private ExperienceRequest experience;

    @NotNull(message = "답변은 필수입니다.")
    @Size(min = 1, max = 3, message = "답변은 1~3개여야 합니다.")
    private List<@Valid @NotNull(message = "답변 항목은 null일 수 없습니다.") ExperienceAnswerRequest> answers;
}
