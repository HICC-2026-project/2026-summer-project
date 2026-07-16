package com.career.recommendation.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 카카오에서 받은 원본 유저 정보(kakao_account/properties 중첩 맵)를
 * CustomOAuth2User로 정규화한다. email은 동의항목 승인 전이면 null일 수 있다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String KAKAO_REGISTRATION_ID = "kakao";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!KAKAO_REGISTRATION_ID.equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "지원하지 않는 로그인 제공자입니다: " + registrationId);
        }

        return parseKakaoUser(oAuth2User);
    }

    private CustomOAuth2User parseKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = String.valueOf(attributes.get("id"));

        Map<String, Object> kakaoAccount = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = asMap(kakaoAccount.get("profile"));
        Map<String, Object> properties = asMap(attributes.get("properties"));

        String nickname = (String) profile.getOrDefault("nickname", properties.get("nickname"));
        String email = (String) kakaoAccount.get("email");

        return new CustomOAuth2User(attributes, KAKAO_REGISTRATION_ID, providerId, nickname, email);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
