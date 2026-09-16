package com.career.recommendation.service;

import com.career.recommendation.dto.user.UserMeResponse;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final UserRepository userRepository;

    public UserMeResponse getMe(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId())
                .orElse(null);

        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId())
                .orElse(null);

        return UserMeResponse.of(user, userSpec, targetJob);
    }

    /**
     * 회원 탈퇴. users 행을 지우면 FK CASCADE(V2·V3·V7·V22)로 스펙·목표·리프레시 토큰·추천·로드맵 캐시가
     * 함께 지워지고, 합격자 제보(passer_data)는 reporter만 NULL이 돼 익명 데이터로 남는다(V20).
     * 저장된 제보 증빙 파일은 데이터와 함께 보존한다 — 검수 증거라서 제보자 탈퇴와 무관하다.
     */
    /**
     * 닉네임 변경. 카카오가 준 닉네임은 첫 로그인 때 복사되고 이후 로그인마다 덮어쓰므로
     * (OAuth2LoginSuccessHandler.upsertUser), 여기서 바꾼 값도 다음 카카오 로그인 때 카카오 닉네임으로 되돌아간다.
     * 그래서 nickname_overridden 플래그를 함께 켜 두고, 핸들러는 플래그가 켜진 사용자의 닉네임을 덮어쓰지 않는다.
     */
    @Transactional
    public UserMeResponse updateNickname(Authentication authentication, String nickname) {
        User user = currentUserService.getCurrentUser(authentication);
        user.setNickname(nickname.trim());
        user.setNicknameOverridden(true);
        return getMe(authentication);
    }

    @Transactional
    public void deleteMe(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        userRepository.delete(user);
    }
}