package com.career.recommendation.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** 관리자 — 활동별 추천 피드백(LIKE/DISLIKE) 집계 한 행. E10-2. */
@Getter
@Builder
public class ActivityFeedbackSummaryResponse {

    private UUID activityId;
    private String activityName;
    private long likeCount;
    private long dislikeCount;
}
