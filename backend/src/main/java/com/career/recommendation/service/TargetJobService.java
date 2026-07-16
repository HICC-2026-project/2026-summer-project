package com.career.recommendation.service;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.TargetJobResponse;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.TargetJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetJobService {

    private final CurrentUserService currentUserService;
    private final TargetJobRepository targetJobRepository;

    @Transactional
    public TargetJobResponse saveOrUpdateMyTarget(
            Authentication authentication,
            TargetJobRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);

        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId())
                .orElseGet(() -> TargetJob.builder()
                        .user(user)
                        .build());

        targetJob.setJobType(request.getJobType());
        targetJob.setCompanySize(request.getCompanySize());
        targetJob.setIndustry(request.getIndustry());

        TargetJob savedTargetJob = targetJobRepository.save(targetJob);

        return TargetJobResponse.from(savedTargetJob);
    }
}