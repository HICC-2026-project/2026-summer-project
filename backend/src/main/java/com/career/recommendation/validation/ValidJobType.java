package com.career.recommendation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열 필드가 {@link com.career.recommendation.domain.JobType} 코드인지 검증한다.
 * 예전엔 DTO마다 regex를 따로 적어서(한쪽은 대소문자 무시, 한쪽은 아님) 직무가 추가되면
 * 두 곳을 같이 고쳐야 했다. null/blank는 통과시킨다 — 필수 여부는 @NotBlank로 따로 건다.
 */
@Documented
@Constraint(validatedBy = JobTypeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJobType {

    String message() default "지원하지 않는 직무 코드입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
