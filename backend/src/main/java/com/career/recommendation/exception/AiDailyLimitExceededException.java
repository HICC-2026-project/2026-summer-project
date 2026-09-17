package com.career.recommendation.exception;

/**
 * 사용자별 하루 AI 호출 시도 상한(AiDailyAttemptLimiter)을 넘겼을 때 던진다.
 *
 * 추천(F-03)·로드맵(F-05)은 한도 초과 시 예외 없이 캐시로 조용히 폴백한다 — 이미 화면에
 * 보여줄 이전 결과가 있기 때문이다. 반면 경험 심층 질문/보강(E11-6)은 캐시할 결과가 없는
 * stateless 요청이라 한도 초과 사실을 클라이언트에 명시적으로 알려야 한다(GlobalExceptionHandler가
 * 429로 변환).
 */
public class AiDailyLimitExceededException extends RuntimeException {

    public AiDailyLimitExceededException(String message) {
        super(message);
    }
}
