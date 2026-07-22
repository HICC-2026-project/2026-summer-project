package com.career.recommendation.controller;

import com.career.recommendation.dto.user.TargetJobRequest;
import com.career.recommendation.dto.user.TargetJobResponse;
import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.dto.user.UserSpecResponse;
import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.service.TargetJobService;
import com.career.recommendation.service.UserService;
import com.career.recommendation.service.UserSpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자", description = "내 프로필 조회와 스펙(F-01)·목표 직무(F-02) 관리")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSpecService userSpecService;
    private final TargetJobService targetJobService;

    @Operation(summary = "내 프로필 조회",
            description = "닉네임과 함께 스펙·목표 직무 등록 여부를 반환한다. 온보딩 진입 분기에 사용.")
    @GetMapping("/me")
    public UserMeResponse getMyProfile(Authentication authentication) {
        return userService.getMe(authentication);
    }

    @Operation(summary = "내 스펙 저장/수정 (F-01)",
            description = "학점·학점 기준값·복수 어학성적·복수 자격증·학년을 저장한다. 이미 있으면 전체를 덮어쓴다(upsert).")
    @PutMapping("/me/spec")
    public UserSpecResponse updateMySpec(
            Authentication authentication,
            @Valid @RequestBody UserSpecRequest request
    ) {
        return userSpecService.saveOrUpdateMySpec(authentication, request);
    }

    @Operation(summary = "목표 직무 저장/수정 (F-02)",
            description = "직무·기업 규모·업계를 저장한다. 이미 있으면 전체를 덮어쓴다(upsert).")
    @PutMapping("/me/target")
    public TargetJobResponse updateMyTarget(
            Authentication authentication,
            @RequestBody TargetJobRequest request
    ) {
        return targetJobService.saveOrUpdateMyTarget(authentication, request);
    }
}
