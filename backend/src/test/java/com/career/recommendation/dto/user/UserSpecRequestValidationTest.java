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
import java.util.Map;
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
    void TOEIC_SPEAKING은_지원하지_않는다() {
        UserSpecRequest request = validRequest();
        LanguageScoreRequest speaking = new LanguageScoreRequest();
        speaking.setType("TOEIC_SPEAKING");
        speaking.setGrade("AL");
        request.setLanguageScores(List.of(speaking));

        assertThat(validationMessages(request))
                .contains("지원하는 어학시험은 TOEIC, TOEFL, OPIC입니다.");
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

    /**
     * 0점은 "미입력"과 구분되지 않아 받지 않는다. 예전엔 @PositiveOrZero + score >= 0으로
     * 0점이 통과해, 사용자가 TOEIC에 0을 적으면 홈 요약에 "어학 1개"가 뜨고 점수 계산에도
     * 없는 성적이 잡혔다(2026-08-11 사용자 제보). FE도 0점 항목을 요청에서 제외하지만,
     * API를 직접 부르는 경우까지 막는 백엔드 방어선을 검증한다.
     */
    @Test
    void TOEIC_점수_0점은_미입력으로_보고_거부한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(toeic(0)));

        assertThat(validationMessages(request))
                .contains("어학 점수는 1 이상이어야 합니다. 점수가 없으면 항목을 비워주세요.");
    }

    @Test
    void TOEFL_점수_0점은_미입력으로_보고_거부한다() {
        UserSpecRequest request = validRequest();
        request.setLanguageScores(List.of(toefl(0)));

        assertThat(validationMessages(request))
                .contains("어학 점수는 1 이상이어야 합니다. 점수가 없으면 항목을 비워주세요.");
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

    // --- 경험 ---

    @Test
    void 경험이_없으면_필드_자체가_없어도_검증을_통과한다() {
        // 경험은 과거/외부 클라이언트 호환을 위해 optional이다 — languageScores·certifications와
        // 달리 @NotNull이 없어 null(필드 미전송)이어도 통과해야 한다.
        UserSpecRequest request = validRequest();
        request.setExperiences(null);

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 경험_제목이_비어있으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(List.of(experience("PROJECT", " ", null)));

        assertThat(validationMessages(request))
                .contains("경험 제목은 필수입니다.");
    }

    @Test
    void 경험_제목이_100자를_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(List.of(experience("PROJECT", "가".repeat(101), null)));

        assertThat(validationMessages(request))
                .contains("경험 제목은 100자 이하여야 합니다.");
    }

    @Test
    void 경험_설명이_500자를_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(List.of(experience("PROJECT", "제목", "가".repeat(501))));

        assertThat(validationMessages(request))
                .contains("경험 설명은 500자 이하여야 합니다.");
    }

    @Test
    void 경험_유형이_올바르지_않으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(List.of(experience("INVALID_TYPE", "제목", null)));

        assertThat(validationMessages(request))
                .contains("올바른 경험 유형이 아닙니다.");
    }

    @Test
    void 경험_유형이_없어도_제목만_있으면_검증을_통과한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(List.of(experience(null, "제목", null)));

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 경험_목록에_null_항목이_있으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(Collections.singletonList(null));

        assertThat(validationMessages(request))
                .contains("경험 항목은 null일 수 없습니다.");
    }

    @Test
    void 경험이_20개를_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        request.setExperiences(
                java.util.stream.IntStream.range(0, 21)
                        .mapToObj(i -> experience("ETC", "경험 " + i, null))
                        .collect(Collectors.toList())
        );

        assertThat(validationMessages(request))
                .contains("경험은 최대 20개까지 저장할 수 있습니다.");
    }

    // --- 경험 출처(source) ---

    @Test
    void 경험_출처가_없어도_제목만_있으면_검증을_통과하고_toMap은_MANUAL이_된다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        request.setExperiences(List.of(exp));

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(exp.toMap()).containsEntry("source", "MANUAL");
    }

    @Test
    void 경험_출처로_GITHUB를_대소문자_구분없이_받을_수_있다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setSource("github");
        request.setExperiences(List.of(exp));

        Set<ConstraintViolation<UserSpecRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(exp.toMap()).containsEntry("source", "GITHUB");
    }

    @Test
    void 경험_출처가_올바르지_않으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setSource("CRAWLED");
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("올바른 경험 출처가 아닙니다.");
    }

    // --- 경험 신규 필드(E11 1단계) ---

    @Test
    void 경험_months가_0이면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setMonths(0);
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("활동 기간은 1개월 이상이어야 합니다.");
    }

    @Test
    void 경험_months가_121이면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setMonths(121);
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("활동 기간은 120개월 이하여야 합니다.");
    }

    @Test
    void 경험_months가_1과_120이면_검증을_통과한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp1 = experience("PROJECT", "제목1", null);
        exp1.setMonths(1);
        ExperienceRequest exp2 = experience("PROJECT", "제목2", null);
        exp2.setMonths(120);
        request.setExperiences(List.of(exp1, exp2));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 경험_stack이_11개를_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setStack(java.util.stream.IntStream.range(0, 11).mapToObj(i -> "lib" + i).collect(Collectors.toList()));
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("사용 기술은 최대 10개까지 저장할 수 있습니다.");
    }

    @Test
    void 경험_role이_100자를_초과하면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setRole("가".repeat(101));
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("역할은 100자 이하여야 합니다.");
    }

    @Test
    void 경험_areas에_미지_코드가_있으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setAreas(List.of("AUTH", "NOT_A_REAL_AREA"));
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("올바른 기여 영역 코드가 아닙니다.");
    }

    @Test
    void 경험_areas는_대소문자_무시하고_정규화되며_중복이_제거된다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setAreas(List.of("auth", "AUTH", " api "));
        request.setExperiences(List.of(exp));

        assertThat(validator.validate(request)).isEmpty();
        assertThat((List<String>) exp.toMap().get("areas")).containsExactly("AUTH", "API");
    }

    @Test
    void 경험_depth에_오타가_있으면_검증에_실패한다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setDepth("IMPLEMENTD");
        request.setExperiences(List.of(exp));

        assertThat(validationMessages(request))
                .contains("올바른 구현 깊이가 아닙니다.");
    }

    @Test
    void 경험_depth를_대소문자_구분없이_받을_수_있다() {
        UserSpecRequest request = validRequest();
        ExperienceRequest exp = experience("PROJECT", "제목", null);
        exp.setDepth("implemented");
        request.setExperiences(List.of(exp));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(exp.toMap()).containsEntry("depth", "IMPLEMENTED");
    }

    @Test
    void 경험_신규_필드가_모두_없으면_toMap에_담기지_않는다() {
        ExperienceRequest exp = experience("PROJECT", "제목", null);

        Map<String, Object> map = exp.toMap();

        assertThat(map).doesNotContainKeys("months", "role", "stack", "areas", "depth");
    }

    private ExperienceRequest experience(String type, String title, String description) {
        ExperienceRequest request = new ExperienceRequest();
        request.setType(type);
        request.setTitle(title);
        request.setDescription(description);
        return request;
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
