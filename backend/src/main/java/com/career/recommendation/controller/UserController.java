package com.career.recommendation.controller;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.TargetJobResponse;
import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.service.TargetJobService;
import com.career.recommendation.service.UserService;
import com.career.recommendation.service.UserSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSpecService userSpecService;
    private final TargetJobService targetJobService;

    @GetMapping("/me")
    public UserMeResponse getMyProfile(Authentication authentication) {
        return userService.getMe(authentication);
    }

    @PutMapping("/me/spec")
    public UserSpecResponse updateMySpec(
            Authentication authentication,
            @RequestBody UserSpecRequest request
    ) {
        return userSpecService.saveOrUpdateMySpec(authentication, request);
    }

    @PutMapping("/me/target")
    public TargetJobResponse updateMyTarget(
            Authentication authentication,
            @RequestBody TargetJobRequest request
    ) {
        return targetJobService.saveOrUpdateMyTarget(authentication, request);
    }
}