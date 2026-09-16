package com.career.recommendation.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
public class ExperienceRequest {

    // type은 선택 입력이다 — null을 그대로 통과시켜야 toMap()에서 "ETC" 기본값으로 채울 수 있다.
    @Pattern(
            regexp = "(?i)(INTERNSHIP|PROJECT|COMPETITION|EXTERNAL|EDUCATION|ETC)",
            message = "올바른 경험 유형이 아닙니다."
    )
    private String type;

    @NotBlank(message = "경험 제목은 필수입니다.")
    @Size(max = 100, message = "경험 제목은 100자 이하여야 합니다.")
    private String title;

    @Size(max = 500, message = "경험 설명은 500자 이하여야 합니다.")
    private String description;

    // source도 type과 같은 이유로 선택 입력이다 — 미기재/blank는 toMap()에서 "MANUAL" 기본값으로
    // 채운다. GithubAnalysisService가 자동 생성하는 항목만 "GITHUB"를 명시적으로 채워 넣는다.
    @Pattern(
            regexp = "(?i)(MANUAL|GITHUB)",
            message = "올바른 경험 출처가 아닙니다."
    )
    private String source;

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", (type == null || type.isBlank())
                ? "ETC"
                : type.trim().toUpperCase(Locale.ROOT));
        result.put("title", title.trim());

        if (description != null && !description.isBlank()) {
            result.put("description", description.trim());
        }

        result.put("source", (source == null || source.isBlank())
                ? "MANUAL"
                : source.trim().toUpperCase(Locale.ROOT));

        return result;
    }
}
