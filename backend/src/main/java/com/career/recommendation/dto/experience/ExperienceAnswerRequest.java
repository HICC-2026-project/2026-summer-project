package com.career.recommendation.dto.experience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 후속 질문 하나에 대한 답변 하나. 답변 원문은 응답 생성 후 어디에도 저장하지 않는다. */
@Getter
@Setter
public class ExperienceAnswerRequest {

    @NotBlank(message = "질문은 필수입니다.")
    @Size(max = 500, message = "질문은 500자 이하여야 합니다.")
    private String question;

    @NotBlank(message = "답변은 필수입니다.")
    @Size(max = 1000, message = "답변은 1000자 이하여야 합니다.")
    private String answer;
}
