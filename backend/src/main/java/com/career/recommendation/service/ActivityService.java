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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;

    public Page<ActivityResponse> getActivities(String type, Pageable pageable) {
        Page<Activity> activities;

        if (type == null || type.isBlank()) {
            activities = activityRepository.findByIsActiveTrue(pageable);
        } else {
            activities = activityRepository.findByTypeAndIsActiveTrue(type, pageable);
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
