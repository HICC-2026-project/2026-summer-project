package com.career.recommendation.dto.user;

import com.career.recommendation.entity.TargetJob;
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

    private TargetJobResponse target;

    public static UserMeResponse of(User user, UserSpec userSpec, TargetJob targetJob) {
        return UserMeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .spec(userSpec == null ? null : UserSpecResponse.from(userSpec))
                .target(targetJob == null ? null : TargetJobResponse.from(targetJob))
                .build();
    }

    public static UserMeResponse of(User user, UserSpec userSpec) {
        return UserMeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .spec(userSpec == null ? null : UserSpecResponse.from(userSpec))
                .target(null)
                .build();
    }
}