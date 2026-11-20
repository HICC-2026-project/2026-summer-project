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

    /**
     * 함수 이름: from()
     * 기능: Activity 엔티티의 모든 필드를 그대로 옮겨 상세 조회용 ActivityResponse를 만든다(description 전문 유지).
     * 파라미터: Activity activity -> 변환할 활동 엔티티
     * 반환값: ActivityResponse -> 엔티티 값을 복사한 응답 DTO
     */
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
     * 함수 이름: fromSummary()
     * 기능: 활동 목록(GET /api/v1/activities)용 응답을 만든다. description이 200자를 넘으면 200자로 자르고
     *       "..."을 붙이며(필드명은 그대로 "description"), 그 이하이면 from()과 동일하다.
     *       상세 조회(from)는 전문을 그대로 유지한다. 목록은 페이지당 여러 건의 활동 description 전문
     *       (무제한 text)을 매번 실어 보내는데, 목록 카드에서는 어차피 일부만 보여주므로 나머지는
     *       낭비되는 응답 크기다.
     * 파라미터: Activity activity -> 변환할 활동 엔티티
     * 반환값: ActivityResponse -> description이 필요 시 잘린 목록용 응답 DTO
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