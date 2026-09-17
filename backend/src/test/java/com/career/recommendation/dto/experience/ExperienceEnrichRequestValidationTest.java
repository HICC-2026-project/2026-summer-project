package com.career.recommendation.dto.experience;

import com.career.recommendation.dto.user.ExperienceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceEnrichRequestValidationTest {

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
    void 정상_요청은_검증을_통과한다() {
        ExperienceEnrichRequest request = validRequest();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void experience가_없으면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setExperience(null);

        assertThat(messages(request)).contains("경험 정보는 필수입니다.");
    }

    @Test
    void experience의_제목이_없으면_중첩_검증이_통과되지_않는다() {
        ExperienceEnrichRequest request = validRequest();
        request.getExperience().setTitle(" ");

        assertThat(messages(request)).contains("경험 제목은 필수입니다.");
    }

    @Test
    void 답변이_없으면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(null);

        assertThat(messages(request)).contains("답변은 필수입니다.");
    }

    @Test
    void 답변이_0개면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(List.of());

        assertThat(messages(request)).contains("답변은 1~3개여야 합니다.");
    }

    @Test
    void 답변이_4개면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(IntStream.range(0, 4).mapToObj(i -> answer("질문" + i, "답변" + i))
                .collect(Collectors.toList()));

        assertThat(messages(request)).contains("답변은 1~3개여야 합니다.");
    }

    @Test
    void 답변이_1개에서_3개면_검증을_통과한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(IntStream.range(0, 3).mapToObj(i -> answer("질문" + i, "답변" + i))
                .collect(Collectors.toList()));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 답변_목록에_null_항목이_있으면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(Collections.singletonList(null));

        assertThat(messages(request)).contains("답변 항목은 null일 수 없습니다.");
    }

    @Test
    void 답변이_1000자를_초과하면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(List.of(answer("질문", "가".repeat(1001))));

        assertThat(messages(request)).contains("답변은 1000자 이하여야 합니다.");
    }

    @Test
    void 답변이_정확히_1000자면_검증을_통과한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(List.of(answer("질문", "가".repeat(1000))));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 답변_텍스트가_빈값이면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(List.of(answer("질문", " ")));

        assertThat(messages(request)).contains("답변은 필수입니다.");
    }

    @Test
    void 질문이_500자를_초과하면_검증에_실패한다() {
        ExperienceEnrichRequest request = validRequest();
        request.setAnswers(List.of(answer("가".repeat(501), "답변")));

        assertThat(messages(request)).contains("질문은 500자 이하여야 합니다.");
    }

    private Set<String> messages(ExperienceEnrichRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private ExperienceEnrichRequest validRequest() {
        ExperienceEnrichRequest request = new ExperienceEnrichRequest();
        ExperienceRequest experience = new ExperienceRequest();
        experience.setType("PROJECT");
        experience.setTitle("결제 API 서버");
        request.setExperience(experience);
        request.setAnswers(List.of(answer("토큰 만료는 어떻게 처리했나요?", "Redis에 TTL을 걸어 자동 만료시켰습니다.")));
        return request;
    }

    private ExperienceAnswerRequest answer(String question, String answer) {
        ExperienceAnswerRequest request = new ExperienceAnswerRequest();
        request.setQuestion(question);
        request.setAnswer(answer);
        return request;
    }
}
