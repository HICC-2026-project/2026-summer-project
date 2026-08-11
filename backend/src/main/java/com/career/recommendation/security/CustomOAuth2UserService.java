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

    /** 파싱 규칙 자체를 네트워크 없이 검증할 수 있게 package-private로 둔다(CustomOAuth2UserServiceTest). */
    CustomOAuth2User parseKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = String.valueOf(attributes.get("id"));

        Map<String, Object> kakaoAccount = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = asMap(kakaoAccount.get("profile"));
        Map<String, Object> properties = asMap(attributes.get("properties"));

        // getOrDefault는 키가 "존재하되 값이 null"이면 기본값을 쓰지 않고 null을 그대로 돌려준다 —
        // 카카오가 profile은 주면서 nickname을 null로 채워 보내면 properties.nickname 폴백이
        // 통째로 무시됐다. 값 기반으로 직접 고른다. 캐스팅도 instanceof로 바꿔, 예상 밖 타입이
        // 왔을 때 ClassCastException이 OAuth2 필터 밖으로 튀어 로그인 전체가 500이 되지 않게 한다.
        String nickname = firstNonBlank(asString(profile.get("nickname")), asString(properties.get("nickname")));
        String email = asString(kakaoAccount.get("email"));

        return new CustomOAuth2User(attributes, KAKAO_REGISTRATION_ID, providerId, nickname, email);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /** 빈 문자열은 "닉네임 없음"과 같이 취급한다(동의 미승인 시 폴백 판단을 일관되게). */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
