package com.career.recommendation.dto.activity;

import com.career.recommendation.entity.Activity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
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

    /** 목록 응답에서 description을 잘라낼 상한. ActivityService.getActivities(목록)에서만 쓰인다. */
    private static final int LIST_DESCRIPTION_LIMIT = 200;

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

    /**
     * 활동 목록(GET /api/v1/activities)용 — description을 200자로 잘라 전송한다(필드명은 그대로
     * "description"). 상세 조회(from)는 전문을 그대로 유지한다. 목록은 페이지당 여러 건의 활동
     * description 전문(무제한 text)을 매번 실어 보내는데, 목록 카드에서는 어차피 일부만
     * 보여주므로 나머지는 낭비되는 응답 크기다.
     */
    public static ActivityResponse fromSummary(Activity activity) {
        ActivityResponse full = from(activity);
        String description = full.getDescription();
        if (description == null || description.length() <= LIST_DESCRIPTION_LIMIT) {
            return full;
        }
        return full.toBuilder()
                .description(description.substring(0, LIST_DESCRIPTION_LIMIT) + "...")
                .build();
    }
}