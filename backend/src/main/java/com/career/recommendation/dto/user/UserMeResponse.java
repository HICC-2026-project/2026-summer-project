package com.career.recommendation.dto.user;

import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;

import java.util.UUID;

public record UserMeResponse(
        UUID id,
        String nickname,
        String provider,
        String role,
        UserSpecResponse spec
) {
    public static UserMeResponse of(User user, UserSpec spec) {
        return new UserMeResponse(
                user.getId(),
                user.getNickname(),
                user.getProvider(),
                user.getRole(),
                spec == null ? null : UserSpecResponse.from(spec)
        );
    }
}
