package com.career.recommendation.dto.activity;

import com.career.recommendation.entity.Activity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 활동 목록(GET /api/v1/activities)은 description을 200자로 잘라 보내고, 상세 조회
 * (GET /api/v1/activities/{id})는 전문을 그대로 보낸다 — 같은 DTO(ActivityResponse)를 쓰지만
 * 목록에서만 fromSummary()를 통해 축약한다(ActivityService.getActivities / getActivity).
 * 필드명은 그대로 "description"이라 FE 계약은 바뀌지 않는다.
 */
class ActivityResponseTest {

    @Test
    void 목록용_fromSummary는_description을_200자로_자른다() {
        String longDescription = "가".repeat(300);
        Activity activity = Activity.builder()
                .id(UUID.randomUUID()).type("EDUCATION").name("부트캠프")
                .description(longDescription).build();

        ActivityResponse summary = ActivityResponse.fromSummary(activity);

        assertThat(summary.getDescription()).isEqualTo("가".repeat(200) + "...");
    }

    @Test
    void 상세용_from은_description_전문을_그대로_유지한다() {
        String longDescription = "가".repeat(300);
        Activity activity = Activity.builder()
                .id(UUID.randomUUID()).type("EDUCATION").name("부트캠프")
                .description(longDescription).build();

        ActivityResponse detail = ActivityResponse.from(activity);

        assertThat(detail.getDescription()).isEqualTo(longDescription);
    }

    @Test
    void 상한보다_짧은_description은_fromSummary도_그대로_둔다() {
        Activity activity = Activity.builder()
                .id(UUID.randomUUID()).type("EDUCATION").name("부트캠프")
                .description("짧은 설명").build();

        assertThat(ActivityResponse.fromSummary(activity).getDescription()).isEqualTo("짧은 설명");
    }

    @Test
    void description이_null이면_fromSummary도_null이다() {
        Activity activity = Activity.builder()
                .id(UUID.randomUUID()).type("EDUCATION").name("부트캠프")
                .description(null).build();

        assertThat(ActivityResponse.fromSummary(activity).getDescription()).isNull();
    }
}
