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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /**
     * 함수 이름: handleInvalidTokenException()
     * 기능: InvalidTokenException(유효하지 않은 토큰)을 HTTP 401 UNAUTHORIZED로 변환한다.
     *       응답 본문은 code "INVALID_TOKEN"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: InvalidTokenException e -> 잡힌 토큰 오류 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 401 상태코드와 에러 본문
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTokenException(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody("INVALID_TOKEN", e.getMessage()));
    }

    /**
     * 함수 이름: handleActivityNotFoundException()
     * 기능: ActivityNotFoundException(존재하지 않는 활동)을 HTTP 404 NOT_FOUND로 변환한다.
     *       응답 본문은 code "ACTIVITY_NOT_FOUND"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: ActivityNotFoundException e -> 잡힌 활동 미존재 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 404 상태코드와 에러 본문
     */
    @ExceptionHandler(ActivityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleActivityNotFoundException(ActivityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("ACTIVITY_NOT_FOUND", e.getMessage()));
    }

    /**
     * 함수 이름: handleDuplicatePasserReportException()
     * 기능: DuplicatePasserReportException(이미 제출된 합격자 리포트와 중복)을 HTTP 409 CONFLICT로 변환한다.
     *       응답 본문은 code "DUPLICATE_PASSER_REPORT"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: DuplicatePasserReportException e -> 잡힌 중복 리포트 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 409 상태코드와 에러 본문
     */
    @ExceptionHandler(DuplicatePasserReportException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePasserReportException(DuplicatePasserReportException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("DUPLICATE_PASSER_REPORT", e.getMessage()));
    }

    /**
     * 함수 이름: handlePasserReportNotFoundException()
     * 기능: PasserReportNotFoundException(존재하지 않는 합격자 리포트)을 HTTP 404 NOT_FOUND로 변환한다.
     *       응답 본문은 code "PASSER_REPORT_NOT_FOUND"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: PasserReportNotFoundException e -> 잡힌 리포트 미존재 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 404 상태코드와 에러 본문
     */
    @ExceptionHandler(PasserReportNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePasserReportNotFoundException(PasserReportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("PASSER_REPORT_NOT_FOUND", e.getMessage()));
    }

    /**
     * 함수 이름: handleInvalidProofFileException()
     * 기능: InvalidProofFileException(증빙 파일이 형식·내용 검증을 통과하지 못함)을 HTTP 400 BAD_REQUEST로 변환한다.
     *       응답 본문은 code "INVALID_PROOF_FILE"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: InvalidProofFileException e -> 잡힌 증빙 파일 오류 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(InvalidProofFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidProofFileException(InvalidProofFileException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PROOF_FILE", e.getMessage()));
    }

    /**
     * 함수 이름: handleGithubUsernameInvalidException()
     * 기능: GithubUsernameInvalidException(형식이 올바르지 않은 GitHub 사용자명)을 HTTP 400 BAD_REQUEST로 변환한다.
     *       응답 본문은 code "INVALID_GITHUB_USERNAME"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: GithubUsernameInvalidException e -> 잡힌 사용자명 오류 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(GithubUsernameInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleGithubUsernameInvalidException(GithubUsernameInvalidException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_GITHUB_USERNAME", e.getMessage()));
    }

    /**
     * 함수 이름: handleGithubAnalysisInProgressException()
     * 기능: GithubAnalysisInProgressException(같은 사용자의 GitHub 분석이 이미 진행 중)을 HTTP 409 CONFLICT로 변환한다.
     *       응답 본문은 code "GITHUB_ANALYSIS_IN_PROGRESS"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: GithubAnalysisInProgressException e -> 잡힌 분석 진행 중 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 409 상태코드와 에러 본문
     */
    @ExceptionHandler(GithubAnalysisInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleGithubAnalysisInProgressException(GithubAnalysisInProgressException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("GITHUB_ANALYSIS_IN_PROGRESS", e.getMessage()));
    }

    /**
     * 함수 이름: handleGithubAnalysisCooldownException()
     * 기능: GithubAnalysisCooldownException(GitHub 재분석 대기 시간이 아직 지나지 않음)을 HTTP 409 CONFLICT로 변환한다.
     *       응답 본문은 code "GITHUB_ANALYSIS_COOLDOWN"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: GithubAnalysisCooldownException e -> 잡힌 재분석 쿨다운 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 409 상태코드와 에러 본문
     */
    @ExceptionHandler(GithubAnalysisCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleGithubAnalysisCooldownException(GithubAnalysisCooldownException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("GITHUB_ANALYSIS_COOLDOWN", e.getMessage()));
    }

    /**
     * 함수 이름: handleAiDailyLimitExceededException()
     * 기능: AiDailyLimitExceededException(E11-6 경험 심층 질문/보강의 사용자별 하루 상한(10회) 초과)을
     *       HTTP 429 TOO_MANY_REQUESTS로 변환한다.
     *       응답 본문은 code "AI_DAILY_LIMIT_EXCEEDED"에 예외 메시지를 담은 errorBody 형식이다.
     * 파라미터: AiDailyLimitExceededException e -> 잡힌 하루 한도 초과 예외(메시지를 응답에 그대로 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 429 상태코드와 에러 본문
     */
    @ExceptionHandler(AiDailyLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleAiDailyLimitExceededException(AiDailyLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(errorBody("AI_DAILY_LIMIT_EXCEEDED", e.getMessage()));
    }

    /**
     * 함수 이름: handleMaxUploadSizeExceededException()
     * 기능: MaxUploadSizeExceededException(업로드 파일이 허용 용량 초과)을 HTTP 413 PAYLOAD_TOO_LARGE로 변환한다.
     *       응답 본문은 code "PROOF_FILE_TOO_LARGE"에 "증빙 파일은 최대 5MB까지 업로드할 수 있습니다." 고정 문구를 담는다.
     * 파라미터: MaxUploadSizeExceededException e -> 잡힌 업로드 용량 초과 예외(메시지는 사용하지 않음)
     * 반환값: ResponseEntity<Map<String, Object>> -> 413 상태코드와 에러 본문
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(errorBody("PROOF_FILE_TOO_LARGE", "증빙 파일은 최대 5MB까지 업로드할 수 있습니다."));
    }

    /**
     * 함수 이름: handleResponseStatusException()
     * 기능: ResponseStatusException이 담고 있는 상태코드를 그대로 응답 상태로 사용해 변환한다.
     *       CurrentUserService의 401/404가 여기로 정확히 매핑되게 한다.
     *       상태코드를 HttpStatus로 해석하지 못하면 500 INTERNAL_SERVER_ERROR로 대체한다.
     *       응답 본문은 code에 상태 이름(예: UNAUTHORIZED, NOT_FOUND), message에 예외의 reason을 담는다.
     * 파라미터: ResponseStatusException e -> 상태코드와 사유(reason)를 가진 예외
     * 반환값: ResponseEntity<Map<String, Object>> -> 예외가 가진 상태코드와 에러 본문
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(errorBody(status.name(), e.getReason()));
    }

    /**
     * 함수 이름: handleTypeMismatch()
     * 기능: URL 경로/쿼리 파라미터 타입 변환 실패(예: UUID·enum 파싱 실패)를 HTTP 400 BAD_REQUEST로 변환한다.
     *       응답 본문은 code "INVALID_PARAMETER"에 "'파라미터명' 파라미터 값이 올바르지 않습니다." 문구를 담는다.
     * 파라미터: MethodArgumentTypeMismatchException e -> 타입 변환에 실패한 파라미터 정보(파라미터 이름 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "'" + e.getName() + "' 파라미터 값이 올바르지 않습니다."));
    }

    /**
     * 함수 이름: handleMissingParameter()
     * 기능: 필수 쿼리 파라미터 누락을 HTTP 400 BAD_REQUEST로 변환한다.
     *       응답 본문은 code "MISSING_PARAMETER"에 "'파라미터명' 파라미터가 필요합니다." 문구를 담는다.
     * 파라미터: MissingServletRequestParameterException e -> 누락된 파라미터 정보(파라미터 이름 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("MISSING_PARAMETER", "'" + e.getParameterName() + "' 파라미터가 필요합니다."));
    }

    /**
     * 함수 이름: handleNotReadable()
     * 기능: 요청 바디를 읽지 못한 경우(깨진 JSON, 빈 바디, 타입이 안 맞는 필드 값 등)를 HTTP 400 BAD_REQUEST로 변환한다.
     *       응답 본문은 code "INVALID_REQUEST_BODY"에 "요청 본문을 읽을 수 없습니다." 고정 문구를 담는다.
     *       ⚠️ 설계 이유: HttpMessageNotReadableException은 RuntimeException 하위라 이 핸들러가 없으면
     * 아래 RuntimeException 핸들러에 잡혀 500 INTERNAL_ERROR가 나갔다(클라이언트 잘못인데
     * 서버 오류로 보고되고, 그때마다 log.error로 에러 로그까지 남았다). @RequestBody를
     * 쓰는 모든 POST/PUT(인증 불필요한 /api/v1/auth/refresh·logout 포함)이 영향받는다.
     * 파싱 실패 상세(필드명·기대 타입 등)는 노출하지 않고 고정 문구만 준다.
     * 파라미터: HttpMessageNotReadableException e -> 요청 바디 파싱 실패 예외(메시지는 사용하지 않음)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_REQUEST_BODY", "요청 본문을 읽을 수 없습니다."));
    }

    /**
     * 함수 이름: handlePropertyReferenceException()
     * 기능: 존재하지 않는 정렬 필드(sortBy) 등 엔티티에 없는 프로퍼티 참조 오류를 HTTP 400 BAD_REQUEST로 변환한다.
     *       내부 엔티티 구조가 메시지에 실릴 수 있어 예외 메시지 대신 code "INVALID_PARAMETER"와
     *       "요청 파라미터 값이 올바르지 않습니다." 고정 문구를 응답에 담는다.
     * 파라미터: PropertyReferenceException e -> 존재하지 않는 프로퍼티 참조 예외(메시지는 사용하지 않음)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Map<String, Object>> handlePropertyReferenceException(PropertyReferenceException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "요청 파라미터 값이 올바르지 않습니다."));
    }

    /**
     * 함수 이름: handleIllegalArgumentException()
     * 기능: IllegalArgumentException을 HTTP 400 BAD_REQUEST로 변환한다. PageRequest.of(page, size, ...)의
     *       자체 검증(page&lt;0, size&lt;=0)이 여기로 떨어진다.
     *       응답 본문은 code "INVALID_PARAMETER"에 "요청 파라미터 값이 올바르지 않습니다." 고정 문구를 담는다.
     *       ⚠️ 주의: IllegalArgumentException은 프로그래밍 오류 신호로도 쓰이는 범용 타입이라 서비스
     * 레이어에서 던지는 다른 의미의 IllegalArgumentException까지 전부 400으로 만들 위험이
     * 있지만, 지금 이 예외를 던지는 지점은 컨트롤러의 요청 파라미터 검증뿐이라 안전하다 —
     * 나중에 서비스 레이어에서 의도가 다른 IllegalArgumentException을 던지게 되면 이 핸들러가
     * 잘못된 400으로 감출 수 있으니, 그때는 전용 예외 타입을 새로 만드는 걸 우선 고려한다.
     * 파라미터: IllegalArgumentException e -> 잘못된 인자 예외(메시지는 사용하지 않음)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(errorBody("INVALID_PARAMETER", "요청 파라미터 값이 올바르지 않습니다."));
    }

    /**
     * 함수 이름: handleRuntimeException()
     * 기능: 위의 구체 핸들러들이 처리하지 못한 예상 밖의 RuntimeException을 HTTP 500 INTERNAL_SERVER_ERROR로 변환한다.
     *       상세 내용은 log.error로 서버 로그에만 남기고, 클라이언트에는 code "INTERNAL_ERROR"와
     *       "서버 내부 오류가 발생했습니다." 고정 문구만 준다(내부 메시지 노출 방지).
     * 파라미터: RuntimeException e -> 처리되지 않은 런타임 예외(로그 기록에만 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 500 상태코드와 에러 본문
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("예상하지 못한 RuntimeException", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    /**
     * 함수 이름: handleValidationException()
     * 기능: @Valid 검증 실패(MethodArgumentNotValidException)를 HTTP 400 BAD_REQUEST로 변환한다.
     *       필드 오류 중 첫 번째를 "필드명: 메시지" 형태로 만들어 code "VALIDATION_ERROR"의 message로 담고,
     *       필드 오류가 하나도 없으면 "유효성 검사 실패"를 사용한다.
     * 파라미터: MethodArgumentNotValidException e -> 요청 바디 검증 실패 예외(BindingResult의 필드 오류 사용)
     * 반환값: ResponseEntity<Map<String, Object>> -> 400 상태코드와 에러 본문
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("유효성 검사 실패");
        return ResponseEntity.badRequest()
                .body(errorBody("VALIDATION_ERROR", message));
    }

    /**
     * 함수 이름: errorBody()
     * 기능: 모든 핸들러가 공통으로 쓰는 에러 응답 본문을 만든다. code, message, timestamp(현재 시각 문자열) 세 키를 가진
     *       불변 Map이며, message가 null이면 빈 문자열로 대체한다.
     * 파라미터: String code -> 에러 종류를 나타내는 코드(예: "INVALID_TOKEN")
     *           String message -> 사용자에게 보여줄 메시지(null 허용)
     * 반환값: Map<String, Object> -> code, message, timestamp를 담은 응답 본문
     */
    private Map<String, Object> errorBody(String code, String message) {
        return Map.of(
                "code", code,
                "message", message != null ? message : "",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
