package com.career.recommendation.dto.github;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /api/v1/users/me/github 요청 바디. "url" 필드명이지만 {@code github.com/{user}} 형태의
 * URL과 순수 username 둘 다 받는다 — 실제 파싱은 GithubUsernameParser가 담당한다.
 */
@Getter
@Setter
public class GithubConnectRequest {

    @NotBlank(message = "GitHub 아이디 또는 URL은 필수입니다.")
    @Size(max = 200, message = "입력값이 너무 깁니다.")
    private String url;
}
