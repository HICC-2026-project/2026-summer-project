package com.career.recommendation.validation;

import com.career.recommendation.domain.JobType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JobTypeValidator implements ConstraintValidator<ValidJobType, String> {

    /**
     * 함수 이름: isValid()
     * 기능: 값이 null 또는 공백이면 통과시키고(필수 여부는 @NotBlank가 따로 담당), 그 외에는
     *       JobType.isValid()로 지원하는 직무 코드인지 검증한다.
     * 파라미터: String value -> 검증할 직무 코드 문자열
     *           ConstraintValidatorContext context -> 검증 컨텍스트(사용하지 않음)
     * 반환값: null/공백이거나 유효한 직무 코드면 true, 아니면 false
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return JobType.isValid(value);
    }
}
