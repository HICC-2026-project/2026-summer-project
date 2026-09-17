package com.career.recommendation.exception;

/** GithubClient 내부용 — 대상 계정이 조직(Organization) 계정일 때 분석을 거부한다. */
public class GithubOrganizationAccountException extends RuntimeException {

    public GithubOrganizationAccountException(String username) {
        super("조직 계정은 분석할 수 없습니다: " + username);
    }
}
