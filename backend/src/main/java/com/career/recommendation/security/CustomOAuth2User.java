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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return providerId;
    }
}
