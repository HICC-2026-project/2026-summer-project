package com.career.recommendation.exception;

/** URL/username 파싱 또는 GitHub username 형식 검증 실패 — 컨트롤러 동기 구간에서 400으로 처리한다. */
public class GithubUsernameInvalidException extends RuntimeException {

    public GithubUsernameInvalidException(String message) {
        super(message);
    }
}
