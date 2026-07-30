package com.career.recommendation.service;

import com.career.recommendation.dto.activity.ActivityResponse;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.exception.ActivityNotFoundException;
import com.career.recommendation.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ActivityRepository activityRepository;

    public Page<ActivityResponse> getActivities(String type, Pageable pageable) {
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);
        Page<Activity> activities;

        if (type == null || type.isBlank()) {
            activities = activityRepository.findOpenActivities(today, pageable);
        } else {
            activities = activityRepository.findOpenActivitiesByType(type, today, pageable);
        }

        return activities.map(ActivityResponse::from);
    }

    public ActivityResponse getActivity(UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(foundActivity -> Boolean.TRUE.equals(foundActivity.getIsActive()))
                .orElseThrow(() -> new ActivityNotFoundException(activityId));

        return ActivityResponse.from(activity);
    }
}
