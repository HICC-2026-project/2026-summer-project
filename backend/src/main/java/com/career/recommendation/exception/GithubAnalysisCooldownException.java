package com.career.recommendation.exception;

/** 마지막 분석(analyzed_at) 이후 24시간 이내 재분석 요청을 막는다(409). PENDING 중복 요청과는 별개 사유. */
public class GithubAnalysisCooldownException extends RuntimeException {

    public GithubAnalysisCooldownException(String message) {
        super(message);
    }
}
