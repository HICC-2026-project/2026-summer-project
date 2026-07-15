package com.career.recommendation.controller;

import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.service.UserSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSpecService userSpecService;

    @GetMapping("/me")
    public UserMeResponse getMyProfile(Authentication authentication) {
        return userSpecService.getMyProfile(authentication);
    }

    @PutMapping("/me/spec")
    public UserSpecResponse updateMySpec(
            Authentication authentication,
            @RequestBody UserSpecRequest request
    ) {
        return userSpecService.saveOrUpdateMySpec(authentication, request);
    }
}