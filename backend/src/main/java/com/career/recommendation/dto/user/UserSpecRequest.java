package com.career.recommendation.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Getter
@Setter
public class UserSpecRequest {

    @NotNull(message = "학점은 필수입니다.")
    @DecimalMin(value = "0.0", message = "학점은 0 이상이어야 합니다.")
    @DecimalMax(value = "4.5", message = "학점은 4.5 이하여야 합니다.")
    @Digits(integer = 1, fraction = 2, message = "학점은 소수점 둘째 자리까지만 입력할 수 있습니다.")
    private BigDecimal gpa;

    @NotNull(message = "학점 기준값은 필수입니다.")
    @DecimalMin(value = "1.0", message = "학점 기준값은 1.0 이상이어야 합니다.")
    @DecimalMax(value = "4.5", message = "학점 기준값은 4.5 이하여야 합니다.")
    @Digits(integer = 1, fraction = 2, message = "학점 기준값은 소수점 둘째 자리까지만 입력할 수 있습니다.")
    private BigDecimal gpaMax;

    @NotNull(message = "어학점수 목록은 필수입니다. 없으면 빈 배열을 보내야 합니다.")
    @Size(max = 10, message = "어학점수는 최대 10개까지 저장할 수 있습니다.")
    private List<
            @NotNull(message = "어학점수 항목은 null일 수 없습니다.")
            @Valid LanguageScoreRequest> languageScores;

    @NotNull(message = "자격증 목록은 필수입니다. 없으면 빈 배열을 보내야 합니다.")
    @Size(max = 30, message = "자격증은 최대 30개까지 저장할 수 있습니다.")
    private List<
            @NotBlank(message = "자격증 이름은 비어 있을 수 없습니다.")
            @Size(max = 100, message = "자격증 이름은 100자 이하여야 합니다.")
            String> certifications;

    @NotNull(message = "학년은 필수입니다.")
    @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
    @Max(value = 4, message = "학년은 4 이하여야 합니다.")
    private Integer grade;

    @AssertTrue(message = "학점은 학점 기준값보다 클 수 없습니다.")
    @JsonIgnore
    public boolean isGpaWithinMaximum() {
        if (gpa == null || gpaMax == null) {
            return true;
        }

        return gpa.compareTo(gpaMax) <= 0;
    }

    @AssertTrue(message = "같은 종류의 어학시험을 중복으로 저장할 수 없습니다.")
    @JsonIgnore
    public boolean isLanguageTypeUnique() {
        if (languageScores == null) {
            return true;
        }

        long validTypeCount = languageScores.stream()
                .filter(Objects::nonNull)
                .map(LanguageScoreRequest::getType)
                .filter(Objects::nonNull)
                .filter(type -> !type.isBlank())
                .count();

        long distinctTypeCount = languageScores.stream()
                .filter(Objects::nonNull)
                .map(LanguageScoreRequest::getType)
                .filter(Objects::nonNull)
                .filter(type -> !type.isBlank())
                .map(type -> type.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .count();

        return validTypeCount == distinctTypeCount;
    }
}
