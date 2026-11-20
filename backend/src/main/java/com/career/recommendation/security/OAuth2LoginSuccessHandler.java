package com.career.recommendation.security;

import com.career.recommendation.entity.User;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * 카카오 로그인 성공 시 provider+providerId 기준으로 유저를 upsert하고
 * JWT를 발급해 프론트로 리다이렉트한다. 토큰은 쿼리 파라미터가 아니라 URL 프래그먼트(#)로
 * 넘겨 서버 로그·Referer에 남지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AdminAccountPolicy adminAccountPolicy;

    @Value("${app.oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;

    /**
     * 함수 이름: onAuthenticationSuccess()
     * 기능: 로그인한 CustomOAuth2User로 유저를 upsert(역할 동기화 포함)하고 액세스·리프레시 토큰을
     *       발급해, 쿼리 파라미터가 아닌 URL 프래그먼트(#)에 담아 프론트 리다이렉트 URI로 보낸다.
     *       프래그먼트는 서버 로그·Referer에 남지 않는다.
     * 파라미터: HttpServletRequest request -> 콜백 요청
     *           HttpServletResponse response -> 리다이렉트를 보낼 응답
     *           Authentication authentication -> principal이 CustomOAuth2User인 인증 결과
     * 반환값: 없음
     * 예외: IOException -> 리다이렉트 전송에 실패했을 때
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = upsertUser(oAuth2User);

        TokenService.TokenPair tokens = tokenService.issueTokens(user);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("accessToken=" + tokens.accessToken() + "&refreshToken=" + tokens.refreshToken())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    /**
     * 함수 이름: upsertUser()
     * 기능: provider+providerId로 기존 유저를 찾아 갱신하거나 없으면 새로 저장한다. 기존 유저는
     *       카카오 닉네임이 실제로 있고 앱에서 직접 바꾼 닉네임(nicknameOverridden)이 아닐 때만
     *       닉네임을 덮어쓰며, 역할은 AdminAccountPolicy 결과로 로그인마다 동기화한다.
     *       신규 유저는 카카오 닉네임과 정책에 따른 역할로 생성한다.
     * 파라미터: CustomOAuth2User oAuth2User -> 카카오에서 정규화된 로그인 사용자 정보
     * 반환값: 저장된 User 엔티티
     */
    private User upsertUser(CustomOAuth2User oAuth2User) {
        return userRepository.findByProviderAndProviderId(oAuth2User.getProvider(), oAuth2User.getProviderId())
                .map(existing -> {
                    // 카카오의 profile_nickname은 선택 동의 항목이라, 이미 닉네임이 있는 유저가
                    // 동의를 철회하고 재로그인하면 닉네임이 null로 내려온다. 그대로 덮어쓰면
                    // 기존 닉네임이 조용히 지워지므로(로그인만 했는데 프로필이 비는 현상),
                    // 값이 실제로 있을 때만 갱신한다.
                    String nickname = oAuth2User.getNickname();
                    // 앱에서 직접 바꾼 닉네임(nicknameOverridden)은 카카오 값으로 되돌리지 않는다.
                    if (nickname != null && !nickname.isBlank() && !existing.isNicknameOverridden()) {
                        existing.setNickname(nickname);
                    }
                    // 관리자 여부는 환경변수(ADMIN_PROVIDER_IDS)로 정해지므로 로그인마다 동기화한다 —
                    // 목록에서 빠진 계정이 다음 로그인에도 ADMIN으로 남지 않게.
                    existing.setRole(adminAccountPolicy.resolveRole(existing.getProvider(), existing.getProviderId()));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(oAuth2User.getProvider())
                        .providerId(oAuth2User.getProviderId())
                        .nickname(oAuth2User.getNickname())
                        .role(adminAccountPolicy.resolveRole(oAuth2User.getProvider(), oAuth2User.getProviderId()))
                        .build()));
    }
}
