package com.career.recommendation.dto.github;

import lombok.Builder;
import lombok.Getter;

/** POST /api/v1/users/me/github 성공(202) 응답. */
@Getter
@Builder
public class GithubConnectResponse {

    private String username;

    private String status;

    /**
     * 함수 이름: of()
     * 기능: GitHub 연동 요청 접수 결과(사용자명, 분석 상태)를 담은 응답 DTO를 만든다.
     * 파라미터: String username -> 연동한 GitHub 사용자명
     *           String status -> 현재 분석 상태
     * 반환값: GithubConnectResponse -> 사용자명과 상태를 담은 응답 DTO
     */
    public static GithubConnectResponse of(String username, String status) {
        return GithubConnectResponse.builder()
                .username(username)
                .status(status)
                .build();
    }
}
