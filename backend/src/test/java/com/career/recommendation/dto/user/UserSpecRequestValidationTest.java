package com.career.recommendation.dto.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserSpecRequestValidationTest {

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
    void 정상적인_UserSpec_요청은_검증을_통과한다() {
        UserSpecRequest request = validRequest();

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void TOEIC_점수가_990점을_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(toeic(1000)));

        assertThat(validationMessages(request))
                .contains("어학점수 형식이 시험 종류와 일치하지 않습니다.");
    }

    @Test
    void TOEIC의_maxScore가_990이_아니면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        LanguageScoreRequest toeic = toeic(850);
        toeic.setMaxScore(120);
        request.setLanguageScores(List.of(toeic));

        assertThat(validationMessages(request))
                .contains("어학점수 형식이 시험 종류와 일치하지 않습니다.");
    }

    @Test
    void TOEFL_점수가_120점을_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(toefl(121)));

        assertThat(validationMessages(request))
                .contains("어학점수 형식이 시험 종류와 일치하지 않습니다.");
    }

    @Test
    void OPIC에_숫자점수를_입력하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        LanguageScoreRequest opic = opic("IH");
        opic.setScore(100);
        request.setLanguageScores(List.of(opic));

        assertThat(validationMessages(request))
                .contains("어학점수 형식이 시험 종류와 일치하지 않습니다.");
    }

    @Test
    void 올바르지_않은_OPIC_등급은_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(opic("INVALID")));

        assertThat(validationMessages(request))
                .contains("올바른 OPIC 등급이 아닙니다.");
    }

    @Test
    void 동일한_어학시험을_두_번_등록하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(toeic(850), toeic(900)));

        assertThat(validationMessages(request))
                .contains("같은 종류의 어학시험을 중복으로 저장할 수 없습니다.");
    }

    @Test
    void 학점이_학점기준값보다_크면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setGpa(new BigDecimal("4.4"));
        request.setGpaMax(new BigDecimal("4.3"));

        assertThat(validationMessages(request))
                .contains("학점은 학점 기준값보다 클 수 없습니다.");
    }

    @Test
    void 학년이_4보다_크면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setGrade(5);

        assertThat(validationMessages(request))
                .contains("학년은 4 이하여야 합니다.");
    }

    @Test
    void 어학점수와_자격증이_없으면_빈_배열로_요청할_수_있다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of());
        request.setCertifications(List.of());

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 어학점수_배열에_null_항목이_있으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(Collections.singletonList(null));

        assertThat(validationMessages(request))
                .contains("어학점수 항목은 null일 수 없습니다.");
    }

    private Set<String> validationMessages(UserSpecRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private UserSpecRequest validRequest() {
        UserSpecRequest request = new UserSpecRequest();
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setGrade(3);
        request.setLanguageScores(List.of(toeic(850), toefl(100), opic("IH")));
        request.setCertifications(List.of("정보처리기사", "SQLD"));
        return request;
    }

    private LanguageScoreRequest toeic(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("TOEIC");
        request.setScore(score);
        request.setMaxScore(990);
        return request;
    }

    private LanguageScoreRequest toefl(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("TOEFL");
        request.setScore(score);
        request.setMaxScore(120);
        return request;
    }

    private LanguageScoreRequest opic(String grade) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("OPIC");
        request.setGrade(grade);
        return request;
    }
}
