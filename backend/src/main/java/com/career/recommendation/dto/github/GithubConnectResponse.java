package com.career.recommendation.dto.github;

import lombok.Builder;
import lombok.Getter;

/** POST /api/v1/users/me/github 성공(202) 응답. */
@Getter
@Builder
public class GithubConnectResponse {

    private String username;

    private String status;

    public static GithubConnectResponse of(String username, String status) {
        return GithubConnectResponse.builder()
                .username(username)
                .status(status)
                .build();
    }
}
