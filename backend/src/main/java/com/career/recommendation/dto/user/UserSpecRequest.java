package com.career.recommendation.dto.user;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UserSpecRequest {

    private BigDecimal gpa;

    private BigDecimal gpaMax;

    private List<Map<String, Object>> languageScores;

    private String[] certifications;

    private Integer grade;
}