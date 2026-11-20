package com.career.recommendation.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final String provider;
    private final String providerId;
    private final String nickname;
    private final String email;

    public CustomOAuth2User(Map<String, Object> attributes, String provider, String providerId,
                             String nickname, String email) {
        this.attributes = attributes;
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.email = email;
    }

    /**
     * 함수 이름: getAuthorities()
     * 기능: OAuth2 로그인 principal의 권한 목록을 돌려준다. 항상 ROLE_USER 하나로 고정이며,
     *       실제 관리자 여부는 로그인 성공 후 발급되는 JWT의 role 클레임으로 결정된다.
     * 파라미터: 없음
     * 반환값: ROLE_USER 권한 하나만 담은 불변 리스트
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * 함수 이름: getName()
     * 기능: principal의 이름으로 제공자 식별자(providerId)를 사용한다.
     * 파라미터: 없음
     * 반환값: providerId 문자열
     */
    @Override
    public String getName() {
        return providerId;
    }
}
