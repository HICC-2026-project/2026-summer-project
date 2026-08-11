package com.career.recommendation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카카오 user-me 응답(kakao_account/properties 중첩 맵) 파싱 규칙 고정.
 *
 * 이 클래스는 테스트가 하나도 없었는데, 카카오의 profile_nickname은 "선택 동의" 항목이라
 * 실제로 nickname이 없는 응답이 온다 — 그때 NPE 없이 null로 흘러야 하고, profile이 없으면
 * properties.nickname으로 폴백해야 한다. 네트워크가 필요한 super.loadUser()는 건너뛰고
 * 파싱 로직(parseKakaoUser)만 직접 검증한다.
 */
class CustomOAuth2UserServiceTest {

    private final CustomOAuth2UserService service = new CustomOAuth2UserService();

    private OAuth2User kakaoResponse(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    }

    @Test
    void 정상_응답에서_닉네임과_providerId를_뽑는다() {
        CustomOAuth2User user = service.parseKakaoUser(kakaoResponse(Map.of(
                "id", 1234567890L,
                "kakao_account", Map.of("profile", Map.of("nickname", "홍길동")),
                "properties", Map.of("nickname", "홍길동")
        )));

        assertThat(user.getProvider()).isEqualTo("kakao");
        assertThat(user.getProviderId()).isEqualTo("1234567890");
        assertThat(user.getNickname()).isEqualTo("홍길동");
        assertThat(user.getName()).isEqualTo("1234567890");
    }

    @Test
    void 닉네임_동의를_안_하면_kakao_account가_통째로_없어도_NPE없이_null이_된다() {
        CustomOAuth2User user = service.parseKakaoUser(kakaoResponse(Map.of("id", 42L)));

        assertThat(user.getProviderId()).isEqualTo("42");
        assertThat(user.getNickname()).isNull();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void profile이_없으면_properties의_닉네임으로_폴백한다() {
        CustomOAuth2User user = service.parseKakaoUser(kakaoResponse(Map.of(
                "id", 7L,
                "kakao_account", Map.of("profile_nickname_needs_agreement", true),
                "properties", Map.of("nickname", "폴백닉")
        )));

        assertThat(user.getNickname()).isEqualTo("폴백닉");
    }

    @Test
    void profile은_있는데_nickname이_null이면_properties로_폴백한다() {
        // Map.of는 null 값을 못 담으므로 HashMap을 쓴다. getOrDefault는 "키 존재 + 값 null"일 때
        // 기본값을 쓰지 않으므로, 이 케이스에서 폴백이 통째로 무시되던 것이 실제 버그였다.
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", null);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 8L);
        attributes.put("kakao_account", Map.of("profile", profile));
        attributes.put("properties", Map.of("nickname", "폴백닉"));

        assertThat(service.parseKakaoUser(kakaoResponse(attributes)).getNickname()).isEqualTo("폴백닉");
    }

    @Test
    void 닉네임이_빈_문자열이면_없는_것으로_보고_폴백한다() {
        CustomOAuth2User user = service.parseKakaoUser(kakaoResponse(Map.of(
                "id", 9L,
                "kakao_account", Map.of("profile", Map.of("nickname", "  ")),
                "properties", Map.of("nickname", "진짜닉")
        )));

        assertThat(user.getNickname()).isEqualTo("진짜닉");
    }

    @Test
    void 예상하지_못한_타입이_와도_ClassCastException으로_로그인을_깨뜨리지_않는다() {
        // kakao_account가 맵이 아닌 값으로 오거나 nickname이 숫자로 와도 파싱 자체는 살아남아야 한다.
        CustomOAuth2User user = service.parseKakaoUser(kakaoResponse(Map.of(
                "id", 10L,
                "kakao_account", "not-a-map",
                "properties", Map.of("nickname", 12345)
        )));

        assertThat(user.getProviderId()).isEqualTo("10");
        assertThat(user.getNickname()).isNull();
    }
}
