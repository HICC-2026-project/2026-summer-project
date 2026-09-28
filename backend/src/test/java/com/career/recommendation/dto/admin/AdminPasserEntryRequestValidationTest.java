package com.career.recommendation.dto.admin;

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

class AdminPasserEntryRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 올바른_요청은_검증을_통과한다() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void 출처가_없으면_검증에_실패한다() {
        AdminPasserEntryRequest request = validRequest();
        request.setSourceNote(null);

        assertThat(messages(validator.validate(request)))
                .contains("출처(URL 또는 메모)는 필수입니다.");
    }

    @Test
    void 출처가_공백만이면_검증에_실패한다() {
        AdminPasserEntryRequest request = validRequest();
        request.setSourceNote("   ");

        assertThat(messages(validator.validate(request)))
                .contains("출처(URL 또는 메모)는 필수입니다.");
    }

    @Test
    void 출처가_500자를_넘으면_검증에_실패한다() {
        AdminPasserEntryRequest request = validRequest();
        request.setSourceNote("a".repeat(501));

        assertThat(messages(validator.validate(request)))
                .contains("출처는 500자 이하여야 합니다.");
    }

    @Test
    void 지원하지_않는_직무는_검증에_실패한다() {
        AdminPasserEntryRequest request = validRequest();
        request.setJobType("DESIGNER");

        assertThat(messages(validator.validate(request)))
                .contains("지원하지 않는 직무 코드입니다.");
    }

    @Test
    void 학점이_학점기준값보다_크면_검증에_실패한다() {
        AdminPasserEntryRequest request = validRequest();
        request.setGpa(new BigDecimal("4.4"));
        request.setGpaMax(new BigDecimal("4.3"));

        assertThat(messages(validator.validate(request)))
                .contains("학점은 학점 기준값보다 클 수 없습니다.");
    }

    @Test
    void githubUsername이나_githubConsent_필드는_존재하지_않는다() {
        // E11-5 동의 원칙 — 관리자 수기 등록은 제3자 GitHub 분석을 하지 않는다.
        assertThat(AdminPasserEntryRequest.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("githubUsername", "githubConsent", "consent");
    }

    private Set<String> messages(Set<ConstraintViolation<AdminPasserEntryRequest>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private AdminPasserEntryRequest validRequest() {
        AdminPasserEntryRequest request = new AdminPasserEntryRequest();
        request.setJobType("BACKEND");
        request.setYear(2026);
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setLanguageScores(List.of(toeic(850)));
        request.setCertifications(List.of("정보처리기사", "SQLD"));
        request.setExperienceCount(2);
        request.setSourceNote("https://blog.example.com/passed-2026");
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
