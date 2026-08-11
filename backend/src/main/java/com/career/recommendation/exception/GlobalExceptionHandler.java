package com.career.recommendation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * ⚠️ @ExceptionHandler(RuntimeException.class)를 넓게 걸어두면, ResponseStatusException처럼
 * Spring이 원래 알아서 정확한 상태 코드로 번역해줄 예외까지 이 핸들러가 가로채 전부 500으로
 * 뭉갠다. @RestControllerAdvice 핸들러는 Spring MVC의 기본 예외 리졸버(DefaultHandlerExceptionResolver
 * 등)보다 항상 먼저 실행되기 때문이다.
 *
 * 실제로 CurrentUserService가 명시적으로 던지는 401(인증 정보 없음)·404(사용자 없음)가
 * 전부 500 INTERNAL_ERROR로 나가고 있었다 — 추천·로드맵·프로필 등 이 서비스를 쓰는 모든 API
 * 에서였다. 활동 목록 조회(GET /api/v1/activities)도 UUID/enum 파싱 실패나 page/size
 * 검증 실패가 전부 500이 됐고, 이 엔드포인트는 인증이 필요 없어(PUBLIC_URLS) 그 500 응답에
 * e.getMessage()가 그대로 실려 내부 예외 메시지(엔티티 프로퍼티명, SQL 조각 등)가
 * 미인증 사용자에게 노출됐다.
 *
 * 아래 구체 예외 핸들러들을 RuntimeException 핸들러보다 먼저 등록해 이 문제를 막는다 —
 * Spring은 가장 구체적인 타입의 @ExceptionHandler를 우선 매칭하므로 선언 순서와 무관하게
 * 항상 이 핸들러들이 먼저 이긴다. 마지막 RuntimeException 핸들러는 정말 예상 못 한 오류에만
 * 도달해야 하므로, 클라이언트에는 e.getMessage()를 실지 않고 고정 문구만 준다(상세는 서버
 * 로그에만 남긴다).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTokenException(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody("INVALID_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(ActivityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleActivityNotFoundException(ActivityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("ACTIVITY_NOT_FOUND", e.getMessage()));
    }

    /** CurrentUserService의 401/404가 여기로 정확히 매핑되게 한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(errorBody(status.name(), e.getReason()));
    }

    /** URL 경로/쿼리 파라미터 타입 변환 실패 (예: UUID·enum 파싱 실패). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "'" + e.getName() + "' 파라미터 값이 올바르지 않습니다."));
    }

    /** 필수 쿼리 파라미터 누락. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("MISSING_PARAMETER", "'" + e.getParameterName() + "' 파라미터가 필요합니다."));
    }

    /**
     * 요청 바디를 읽지 못한 경우 — 깨진 JSON, 빈 바디, 타입이 안 맞는 필드 값 등.
     * HttpMessageNotReadableException은 RuntimeException 하위라 이 핸들러가 없으면
     * 아래 RuntimeException 핸들러에 잡혀 500 INTERNAL_ERROR가 나갔다(클라이언트 잘못인데
     * 서버 오류로 보고되고, 그때마다 log.error로 에러 로그까지 남았다). @RequestBody를
     * 쓰는 모든 POST/PUT(인증 불필요한 /api/v1/auth/refresh·logout 포함)이 영향받는다.
     * 파싱 실패 상세(필드명·기대 타입 등)는 노출하지 않고 고정 문구만 준다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_REQUEST_BODY", "요청 본문을 읽을 수 없습니다."));
    }

    /** 존재하지 않는 정렬 필드(sortBy) 등 — 내부 엔티티 구조가 메시지에 실릴 수 있어 고정 문구로 대체한다. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Map<String, Object>> handlePropertyReferenceException(PropertyReferenceException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "요청 파라미터 값이 올바르지 않습니다."));
    }

    /**
     * PageRequest.of(page, size, ...)의 자체 검증(page&lt;0, size&lt;=0)이 여기로 떨어진다.
     * IllegalArgumentException은 프로그래밍 오류 신호로도 쓰이는 범용 타입이라 서비스
     * 레이어에서 던지는 다른 의미의 IllegalArgumentException까지 전부 400으로 만들 위험이
     * 있지만, 지금 이 예외를 던지는 지점은 컨트롤러의 요청 파라미터 검증뿐이라 안전하다 —
     * 나중에 서비스 레이어에서 의도가 다른 IllegalArgumentException을 던지게 되면 이 핸들러가
     * 잘못된 400으로 감출 수 있으니, 그때는 전용 예외 타입을 새로 만드는 걸 우선 고려한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "요청 파라미터 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("예상하지 못한 RuntimeException", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("유효성 검사 실패");
        return ResponseEntity.badRequest()
                .body(errorBody("VALIDATION_ERROR", message));
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of(
                "code", code,
                "message", message != null ? message : "",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
