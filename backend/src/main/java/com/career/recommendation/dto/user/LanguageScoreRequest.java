package com.career.recommendation.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
public class LanguageScoreRequest {

    @NotBlank(message = "어학시험 종류는 필수입니다.")
    @Pattern(
            regexp = "(?i)(TOEIC|TOEFL|OPIC)",
            message = "지원하는 어학시험은 TOEIC, TOEFL, OPIC입니다."
    )
    private String type;

    // 0점은 "미입력"과 구분되지 않는 값이라 받지 않는다(실제 TOEIC 최저점도 0이 아니다).
    // 0을 허용하면 화면 요약("어학 1개")과 점수 계산에 없는 성적이 있는 것처럼 잡힌다.
    // FE도 0점 항목은 요청에서 아예 제외하지만, API를 직접 부르는 경우까지 막는 방어선이다.
    @Positive(message = "어학 점수는 1 이상이어야 합니다. 점수가 없으면 항목을 비워주세요.")
    private Integer score;

    @Positive(message = "어학시험 최고 점수는 0보다 커야 합니다.")
    private Integer maxScore;

    @Pattern(
            regexp = "(?i)(NL|NM|NH|IL|IM|IM1|IM2|IM3|IH|AL)",
            message = "올바른 OPIC 등급이 아닙니다."
    )
    private String grade;

    @AssertTrue(message = "어학점수 형식이 시험 종류와 일치하지 않습니다.")
    @JsonIgnore
    public boolean isValidStructure() {
        if (type == null || type.isBlank()) {
            return true;
        }

        return switch (normalizedType()) {
            case "TOEIC" ->
                    score != null
                            && score >= 1
                            && score <= 990
                            && Integer.valueOf(990).equals(maxScore)
                            && grade == null;
            case "TOEFL" ->
                    score != null
                            && score >= 1
                            && score <= 120
                            && Integer.valueOf(120).equals(maxScore)
                            && grade == null;
            case "OPIC" ->
                    grade != null
                            && !grade.isBlank()
                            && score == null
                            && maxScore == null;
            default -> false;
        };
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", normalizedType());

        if (score != null) {
            result.put("score", score);
        }

        if (maxScore != null) {
            result.put("maxScore", maxScore);
        }

        if (grade != null && !grade.isBlank()) {
            result.put("grade", grade.toUpperCase(Locale.ROOT));
        }

        return result;
    }

    private String normalizedType() {
        return type == null
                ? null
                : type.trim().toUpperCase(Locale.ROOT);
    }
}
