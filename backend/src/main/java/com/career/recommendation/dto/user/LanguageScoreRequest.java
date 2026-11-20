package com.career.recommendation.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

    /**
     * 함수 이름: isValidStructure()
     * 기능: 어학점수 형식이 시험 종류와 일치하는지 검증한다. type이 비어 있으면 true(다른 검증에 맡김).
     *       TOEIC은 점수 1~990·만점 990·등급 없음, TOEFL은 점수 1~120·만점 120·등급 없음,
     *       OPIC은 등급만 있고 점수·만점은 없을 때 true이며, 그 외 종류는 false이다.
     * 파라미터: 없음
     * 반환값: boolean -> 검증 통과 여부(true: 형식 일치, false: 시험 종류와 불일치)
     */
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

    /**
     * 함수 이름: toMap()
     * 기능: 어학점수를 JSONB 저장용 Map으로 변환한다. type은 정규화한 값을 넣고, score·maxScore·grade는 값이 있을 때만
     *       넣으며 grade는 대문자로 바꾼다.
     * 파라미터: 없음
     * 반환값: Map<String, Object> -> 저장용 어학점수 항목(삽입 순서 유지)
     */
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

    /**
     * 함수 이름: normalizedType()
     * 기능: 시험 종류 문자열의 앞뒤 공백을 제거하고 대문자로 바꾼다.
     * 파라미터: 없음
     * 반환값: String -> 정규화된 시험 종류, type이 null이면 null
     */
    private String normalizedType() {
        return type == null
                ? null
                : type.trim().toUpperCase(Locale.ROOT);
    }
}
