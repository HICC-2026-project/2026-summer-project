package com.career.recommendation.dto.experience;

import com.career.recommendation.dto.user.ExperienceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceQuestionsRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 제목만_있어도_검증을_통과한다() {
        ExperienceQuestionsRequest request = new ExperienceQuestionsRequest();
        ExperienceRequest experience = new ExperienceRequest();
        experience.setTitle("결제 API 서버");
        request.setExperience(experience);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void experience가_없으면_검증에_실패한다() {
        ExperienceQuestionsRequest request = new ExperienceQuestionsRequest();

        assertThat(messages(request)).contains("경험 정보는 필수입니다.");
    }

    @Test
    void experience의_제목이_없으면_기존_ExperienceRequest_검증이_그대로_적용된다() {
        ExperienceQuestionsRequest request = new ExperienceQuestionsRequest();
        request.setExperience(new ExperienceRequest());

        assertThat(messages(request)).contains("경험 제목은 필수입니다.");
    }

    private Set<String> messages(ExperienceQuestionsRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
