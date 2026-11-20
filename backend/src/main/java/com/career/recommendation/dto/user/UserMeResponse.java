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

    /**
     * 함수 이름: of()
     * 기능: 사용자 기본 정보에 스펙과 목표 직무를 함께 담은 내 정보 응답을 만든다. userSpec·targetJob이 null이면
     *       해당 필드(spec, target)는 null로 둔다.
     * 파라미터: User user -> 사용자 엔티티
     *           UserSpec userSpec -> 사용자 스펙 엔티티(없으면 null)
     *           TargetJob targetJob -> 목표 직무 엔티티(없으면 null)
     * 반환값: UserMeResponse -> 내 정보 응답 DTO
     */
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

    /**
     * 함수 이름: of()
     * 기능: 목표 직무 없이 사용자 기본 정보와 스펙만 담은 내 정보 응답을 만든다(target은 항상 null).
     *       userSpec이 null이면 spec도 null이다.
     * 파라미터: User user -> 사용자 엔티티
     *           UserSpec userSpec -> 사용자 스펙 엔티티(없으면 null)
     * 반환값: UserMeResponse -> 내 정보 응답 DTO
     */
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