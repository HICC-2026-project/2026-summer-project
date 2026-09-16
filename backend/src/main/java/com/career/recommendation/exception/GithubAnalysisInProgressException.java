package com.career.recommendation.exception;

/** 이미 PENDING 상태인 분석이 진행 중일 때 새 분석 요청을 막는다(409). */
public class GithubAnalysisInProgressException extends RuntimeException {

    public GithubAnalysisInProgressException(String message) {
        super(message);
    }
}
