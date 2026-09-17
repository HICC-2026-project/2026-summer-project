package com.career.recommendation.dto.experience;

import com.career.recommendation.dto.user.ExperienceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * E11-6 — POST /api/v1/users/me/experiences/questions 요청 바디.
 * experience는 기존 {@link ExperienceRequest} 검증(제목 필수 등)을 그대로 재사용한다.
 * stateless 엔드포인트라 저장하지 않는다 — 이 요청 자체도 응답을 만드는 데만 쓰이고 폐기된다.
 */
@Getter
@Setter
public class ExperienceQuestionsRequest {

    @NotNull(message = "경험 정보는 필수입니다.")
    @Valid
    private ExperienceRequest experience;
}
