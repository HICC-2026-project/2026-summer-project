package com.career.recommendation.dto.passer;

import com.career.recommendation.dto.user.LanguageScoreRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasserReportRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 올바른_제보는_검증을_통과한다() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void 데이터_이용에_동의하지_않으면_검증에_실패한다() {
        PasserReportRequest request = validRequest();
        request.setConsent(false);

        assertThat(messages(validator.validate(request)))
                .contains("데이터 이용에 동의해야 제보할 수 있습니다.");
    }

    @Test
    void 지원하지_않는_직무는_검증에_실패한다() {
        PasserReportRequest request = validRequest();
        request.setJobType("DESIGNER");

        assertThat(messages(validator.validate(request)))
                .contains("지원하지 않는 직무 코드입니다.");
    }

    @Test
    void 학점이_학점기준값보다_크면_검증에_실패한다() {
        PasserReportRequest request = validRequest();
        request.setGpa(new BigDecimal("4.4"));
        request.setGpaMax(new BigDecimal("4.3"));

        assertThat(messages(validator.validate(request)))
                .contains("학점은 학점 기준값보다 클 수 없습니다.");
    }

    private Set<String> messages(Set<ConstraintViolation<PasserReportRequest>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private PasserReportRequest validRequest() {
        PasserReportRequest request = new PasserReportRequest();
        request.setJobType("BACKEND");
        request.setYear(2026);
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setLanguageScores(List.of(toeic(850)));
        request.setCertifications(List.of("정보처리기사", "SQLD"));
        request.setExperienceCount(2);
        request.setConsent(true);
        return request;
    }

    private LanguageScoreRequest toeic(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("TOEIC");
        request.setScore(score);
        request.setMaxScore(990);
        return request;
    }
}
