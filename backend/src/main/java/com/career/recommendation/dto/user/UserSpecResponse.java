package com.career.recommendation.dto.user;

import com.career.recommendation.entity.UserSpec;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class UserSpecResponse {

    private UUID id;

    private UUID userId;

    private BigDecimal gpa;

    private BigDecimal gpaMax;

    private List<Map<String, Object>> languageScores;

    private String[] certifications;

    private Integer grade;

    private LocalDateTime updatedAt;

    public static UserSpecResponse from(UserSpec userSpec) {
        return UserSpecResponse.builder()
                .id(userSpec.getId())
                .userId(userSpec.getUser().getId())
                .gpa(userSpec.getGpa())
                .gpaMax(userSpec.getGpaMax())
                .languageScores(userSpec.getLanguageScores())
                .certifications(userSpec.getCertifications())
                .grade(userSpec.getGrade())
                .updatedAt(userSpec.getUpdatedAt())
                .build();
    }
}
