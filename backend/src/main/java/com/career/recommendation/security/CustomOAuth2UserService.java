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

    /**
     * 함수 이름: loadUser()
     * 기능: 기본 서비스로 제공자 유저 정보를 조회한 뒤, 등록 ID가 kakao가 아니면 unsupported_provider
     *       오류로 거부한다. kakao인 경우에만 parseKakaoUser()로 CustomOAuth2User로 정규화한다.
     * 파라미터: OAuth2UserRequest userRequest -> 클라이언트 등록 정보와 액세스 토큰을 담은 유저 정보 요청
     * 반환값: 정규화된 CustomOAuth2User(OAuth2User)
     * 예외: OAuth2AuthenticationException -> 지원하지 않는 로그인 제공자일 때
     */
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

    /**
     * 함수 이름: parseKakaoUser()
     * 기능: 카카오 원본 속성에서 id(providerId), kakao_account.profile.nickname(없으면
     *       properties.nickname 폴백), kakao_account.email을 뽑아 CustomOAuth2User를 만든다.
     *       파싱 규칙 자체를 네트워크 없이 검증할 수 있게 package-private로 둔다(CustomOAuth2UserServiceTest).
     * 파라미터: OAuth2User oAuth2User -> 카카오에서 받은 원본 유저 정보
     * 반환값: provider가 kakao로 채워진 CustomOAuth2User (닉네임·이메일은 없으면 null)
     */
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

    /**
     * 함수 이름: asMap()
     * 기능: 값이 Map이면 그대로, 아니면 빈 Map을 돌려준다. 예상 밖 타입이 와도 ClassCastException이
     *       나지 않게 instanceof로 안전하게 변환한다.
     * 파라미터: Object value -> 변환할 값
     * 반환값: Map&lt;String, Object&gt; (Map이 아니면 빈 불변 Map)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    /**
     * 함수 이름: asString()
     * 기능: 값이 String이면 그대로, 아니면 null을 돌려준다.
     * 파라미터: Object value -> 변환할 값
     * 반환값: String 값 또는 null
     */
    private String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /**
     * 함수 이름: firstNonBlank()
     * 기능: 첫 번째 값이 null/공백이 아니면 그것을, 아니면 두 번째 값을 고른다. 둘 다 비었으면 null.
     *       빈 문자열은 "닉네임 없음"과 같이 취급한다(동의 미승인 시 폴백 판단을 일관되게).
     * 파라미터: String first -> 우선 사용할 값
     *           String second -> 첫 번째가 비었을 때 쓸 폴백 값
     * 반환값: 비어 있지 않은 첫 값, 없으면 null
     */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
