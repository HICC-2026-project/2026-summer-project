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

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", (type == null || type.isBlank())
                ? "ETC"
                : type.trim().toUpperCase(Locale.ROOT));
        result.put("title", title.trim());

        if (description != null && !description.isBlank()) {
            result.put("description", description.trim());
        }

        return result;
    }
}
