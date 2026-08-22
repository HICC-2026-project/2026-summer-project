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

    /** USER | ADMIN — 프론트가 검수 화면 진입 링크를 보여줄지 결정하는 데만 쓴다(권한 판정은 서버). */
    private String role;

    private UserSpecResponse spec;

    private TargetJobResponse target;

    public static UserMeResponse of(User user, UserSpec userSpec, TargetJob targetJob) {
        return UserMeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .role(user.getRole())
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
                .role(user.getRole())
                .spec(userSpec == null ? null : UserSpecResponse.from(userSpec))
                .target(null)
                .build();
    }
}