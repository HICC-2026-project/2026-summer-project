package com.career.recommendation.dto.user;

import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserMeResponse {

    private UUID id;

    private String email;

    private String nickname;

    private String provider;

    private UserSpecResponse spec;

    public static UserMeResponse from(User user, UserSpec userSpec) {
        return UserMeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .spec(userSpec == null ? null : UserSpecResponse.from(userSpec))
                .build();
    }
}