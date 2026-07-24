package com.career.recommendation.exception;

import java.util.UUID;

public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException(UUID activityId) {
        super("활동을 찾을 수 없습니다. id=" + activityId);
    }
}
