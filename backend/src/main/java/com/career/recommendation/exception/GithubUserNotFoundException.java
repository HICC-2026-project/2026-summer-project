package com.career.recommendation.exception;

/** GithubClient 내부용 — GET /users/{u}가 404를 돌려줄 때. 비동기 분석 중 잡혀 FAILED 상태로 변환된다. */
public class GithubUserNotFoundException extends RuntimeException {

    public GithubUserNotFoundException(String username) {
        super("존재하지 않는 GitHub 계정입니다: " + username);
    }
}
