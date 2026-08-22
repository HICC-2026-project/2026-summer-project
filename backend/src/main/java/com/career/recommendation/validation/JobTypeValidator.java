package com.career.recommendation.validation;

import com.career.recommendation.domain.JobType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JobTypeValidator implements ConstraintValidator<ValidJobType, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return JobType.isValid(value);
    }
}
