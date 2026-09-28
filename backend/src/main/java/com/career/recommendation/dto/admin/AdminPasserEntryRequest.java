package com.career.recommendation.dto.admin;

import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.validation.ValidJobType;
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

/**
 * 관리자가 공개 커뮤니티·블로그의 합격 후기를 보고 합격자 데이터를 직접 등록할 때 쓰는 요청.
 *
 * PasserReportRequest(사용자 제보)와 스펙 필드는 동일하지만 다음이 다르다:
 * - githubUsername/githubConsent 없음 — 본인 동의 없는 제3자 GitHub 분석은 E11-5 동의 원칙 위반.
 * - consent 없음 — 데이터 이용 동의는 "본인이 직접 낸 제보"에만 의미가 있다.
 * - sourceNote 필수 — 출처 없는 수기 입력을 막는다.
 * 증빙 파일, 24시간 상한, 중복 제보 차단 같은 제보용 제한도 적용하지 않는다(관리자 신뢰 — AdminPasserEntryService 참고).
 */
@Getter
@Setter
public class AdminPasserEntryRequest {

    @NotBlank(message = "목표 직무는 필수입니다.")
    @ValidJobType
    private String jobType;

    @NotNull(message = "합격 연도는 필수입니다.")
    @Min(value = 2000, message = "합격 연도는 2000년 이상이어야 합니다.")
    @Max(value = 2100, message = "합격 연도는 2100년 이하여야 합니다.")
    private Integer year;

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
    @Size(max = 10, message = "어학점수는 최대 10개까지 등록할 수 있습니다.")
    private List<
            @NotNull(message = "어학점수 항목은 null일 수 없습니다.")
            @Valid LanguageScoreRequest> languageScores;

    @NotNull(message = "자격증 목록은 필수입니다. 없으면 빈 배열을 보내야 합니다.")
    @Size(max = 30, message = "자격증은 최대 30개까지 등록할 수 있습니다.")
    private List<
            @NotBlank(message = "자격증 이름은 비어 있을 수 없습니다.")
            @Size(max = 100, message = "자격증 이름은 100자 이하여야 합니다.")
            String> certifications;

    @NotNull(message = "경험 개수는 필수입니다.")
    @Min(value = 0, message = "경험 개수는 0 이상이어야 합니다.")
    @Max(value = 100, message = "경험 개수는 100 이하여야 합니다.")
    private Integer experienceCount;

    /** 출처 URL 또는 메모. 공개된 후기의 근거 없이 수기 등록하는 것을 막기 위해 필수로 받는다. */
    @NotBlank(message = "출처(URL 또는 메모)는 필수입니다.")
    @Size(max = 500, message = "출처는 500자 이하여야 합니다.")
    private String sourceNote;

    @AssertTrue(message = "학점은 학점 기준값보다 클 수 없습니다.")
    @JsonIgnore
    public boolean isGpaWithinMaximum() {
        if (gpa == null || gpaMax == null) {
            return true;
        }
        return gpa.compareTo(gpaMax) <= 0;
    }

    @AssertTrue(message = "같은 종류의 어학시험을 중복으로 등록할 수 없습니다.")
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
