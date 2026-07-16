package com.career.recommendation.dto.user;

import com.career.recommendation.entity.UserSpec;

import java.math.BigDecimal;
import java.util.Map;

public record UserSpecResponse(
        BigDecimal gpa,
        BigDecimal gpaMax,
        Map<String, String> languageScores,
        String[] certifications
) {
    public static UserSpecResponse from(UserSpec spec) {
        return new UserSpecResponse(
                spec.getGpa(),
                spec.getGpaMax(),
                spec.getLanguageScores(),
                spec.getCertifications()
        );
    }
}
