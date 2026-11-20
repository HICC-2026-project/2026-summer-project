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

    private List<Map<String, Object>> experiences;

    private Integer grade;

    private LocalDateTime updatedAt;

    /**
     * 함수 이름: from()
     * 기능: UserSpec 엔티티를 응답 DTO로 변환한다(사용자 ID, 학점, 어학점수, 자격증, 경험, 학년, 수정 시각).
     * 파라미터: UserSpec userSpec -> 변환할 사용자 스펙 엔티티
     * 반환값: UserSpecResponse -> 스펙 응답 DTO
     */
    public static UserSpecResponse from(UserSpec userSpec) {
        return UserSpecResponse.builder()
                .id(userSpec.getId())
                .userId(userSpec.getUser().getId())
                .gpa(userSpec.getGpa())
                .gpaMax(userSpec.getGpaMax())
                .languageScores(userSpec.getLanguageScores())
                .certifications(userSpec.getCertifications())
                .experiences(userSpec.getExperiences())
                .grade(userSpec.getGrade())
                .updatedAt(userSpec.getUpdatedAt())
                .build();
    }
}
