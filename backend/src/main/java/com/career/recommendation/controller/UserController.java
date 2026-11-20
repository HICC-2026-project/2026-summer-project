package com.career.recommendation.controller;

import com.career.recommendation.dto.user.NicknameRequest;
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
import org.springframework.http.HttpStatus;
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

    /**
     * 함수 이름: getMyProfile()
     * 기능: 닉네임과 함께 스펙·목표 직무 등록 여부를 반환한다(온보딩 진입 분기에 사용).
     *       GET /api/v1/users/me, 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     * 반환값: UserMeResponse -> 내 프로필(닉네임, 스펙·목표 직무 등록 여부)
     */
    @Operation(summary = "내 프로필 조회",
            description = "닉네임과 함께 스펙·목표 직무 등록 여부를 반환한다. 온보딩 진입 분기에 사용.")
    @GetMapping("/me")
    public UserMeResponse getMyProfile(Authentication authentication) {
        return userService.getMe(authentication);
    }

    /**
     * 함수 이름: updateMySpec()
     * 기능: 학점·학점 기준값·복수 어학성적·복수 자격증·학년·경험 리스트(최대 20개)를 저장한다.
     *       이미 있으면 전체를 덮어쓴다(upsert).
     *       PUT /api/v1/users/me/spec, 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           UserSpecRequest request -> 저장할 스펙 내용(@Valid 검증)
     * 반환값: UserSpecResponse -> 저장된 스펙 응답
     */
    @Operation(summary = "내 스펙 저장/수정 (F-01)",
            description = "학점·학점 기준값·복수 어학성적·복수 자격증·학년·경험 리스트(최대 20개)를 저장한다. 이미 있으면 전체를 덮어쓴다(upsert).")
    @PutMapping("/me/spec")
    public UserSpecResponse updateMySpec(
            Authentication authentication,
            @Valid @RequestBody UserSpecRequest request
    ) {
        return userSpecService.saveOrUpdateMySpec(authentication, request);
    }

    /**
     * 함수 이름: updateMyTarget()
     * 기능: 목표 직무·기업 규모·업계를 저장한다. 이미 있으면 전체를 덮어쓴다(upsert).
     *       PUT /api/v1/users/me/target, 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           TargetJobRequest request -> 저장할 목표 직무 내용(@Valid 검증)
     * 반환값: TargetJobResponse -> 저장된 목표 직무 응답
     */
    @Operation(summary = "목표 직무 저장/수정 (F-02)",
            description = "직무·기업 규모·업계를 저장한다. 이미 있으면 전체를 덮어쓴다(upsert).")
    @PutMapping("/me/target")
    public TargetJobResponse updateMyTarget(
            Authentication authentication,
            @Valid @RequestBody TargetJobRequest request
    ) {
        return targetJobService.saveOrUpdateMyTarget(authentication, request);
    }

    /**
     * 함수 이름: updateNickname()
     * 기능: 닉네임을 변경한다. 앱에서 바꾼 닉네임은 이후 카카오 로그인 시 카카오 프로필 닉네임으로
     *       덮어써지지 않는다.
     *       PATCH /api/v1/users/me/nickname, 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           NicknameRequest request -> 새 닉네임을 담은 요청(@Valid 검증)
     * 반환값: UserMeResponse -> 변경 후 내 프로필
     */
    @Operation(summary = "닉네임 변경",
            description = "앱에서 바꾼 닉네임은 이후 카카오 로그인 시 카카오 프로필 닉네임으로 덮어써지지 않는다.")
    @PatchMapping("/me/nickname")
    public UserMeResponse updateNickname(
            Authentication authentication,
            @Valid @RequestBody NicknameRequest request
    ) {
        return userService.updateNickname(authentication, request.getNickname());
    }

    /**
     * 함수 이름: deleteMe()
     * 기능: 계정과 스펙·목표·추천·로드맵·리프레시 토큰을 삭제한다(회원 탈퇴). 제보한 합격자 데이터는
     *       익명으로 남으며, 되돌릴 수 없다.
     *       DELETE /api/v1/users/me (204 No Content), 로그인 필요.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     * 반환값: 없음
     */
    @Operation(summary = "회원 탈퇴",
            description = "계정과 스펙·목표·추천·로드맵·리프레시 토큰을 삭제한다. 제보한 합격자 데이터는 익명으로 남는다. 되돌릴 수 없다.")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(Authentication authentication) {
        userService.deleteMe(authentication);
    }
}
