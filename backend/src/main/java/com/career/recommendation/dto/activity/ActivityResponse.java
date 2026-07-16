package com.career.recommendation.dto.activity;

import com.career.recommendation.entity.Activity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class ActivityResponse {

    private UUID id;

    private String type;

    private String name;

    private String organization;

    private String description;

    private LocalDate deadline;

    private LocalDate startDate;

    private LocalDate endDate;

    private Map<String, Object> targetSpec;

    private String[] tags;

    private String url;

    private Boolean isActive;

    private LocalDateTime createdAt;

    public static ActivityResponse from(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .type(activity.getType())
                .name(activity.getName())
                .organization(activity.getOrganization())
                .description(activity.getDescription())
                .deadline(activity.getDeadline())
                .startDate(activity.getStartDate())
                .endDate(activity.getEndDate())
                .targetSpec(activity.getTargetSpec())
                .tags(activity.getTags())
                .url(activity.getUrl())
                .isActive(activity.getIsActive())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}