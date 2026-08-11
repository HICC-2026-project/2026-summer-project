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

    @Value("${app.oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;

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

    private User upsertUser(CustomOAuth2User oAuth2User) {
        return userRepository.findByProviderAndProviderId(oAuth2User.getProvider(), oAuth2User.getProviderId())
                .map(existing -> {
                    // 카카오의 profile_nickname은 선택 동의 항목이라, 이미 닉네임이 있는 유저가
                    // 동의를 철회하고 재로그인하면 닉네임이 null로 내려온다. 그대로 덮어쓰면
                    // 기존 닉네임이 조용히 지워지므로(로그인만 했는데 프로필이 비는 현상),
                    // 값이 실제로 있을 때만 갱신한다.
                    String nickname = oAuth2User.getNickname();
                    if (nickname != null && !nickname.isBlank()) {
                        existing.setNickname(nickname);
                    }
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(oAuth2User.getProvider())
                        .providerId(oAuth2User.getProviderId())
                        .nickname(oAuth2User.getNickname())
                        .build()));
    }
}
