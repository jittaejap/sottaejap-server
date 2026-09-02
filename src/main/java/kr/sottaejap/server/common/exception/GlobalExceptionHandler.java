package kr.sottaejap.server.common.exception;

import kr.sottaejap.server.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

/**
 * Controller 밖으로 나온 예외를 공통 봉투로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (exception.getCause() == null) {
            log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), errorCode.getMessage());
        } else {
            log.error("[BusinessException] code={}, message={}", errorCode.getCode(), errorCode.getMessage(), exception);
        }
        return createErrorResponse(errorCode);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson() {
        return createErrorResponse(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT));
    }

    /**
     * `@Valid` 실패(MethodArgumentNotValidException), 쿼리 바인딩 실패, 타입 변환 실패, 필수 파라미터·헤더 누락.
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            TypeMismatchException.class,
            ServletRequestBindingException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput() {
        return createErrorResponse(CommonErrorCode.INVALID_INPUT);
    }

    /**
     * 업로드 상한 초과 등 multipart 해석 실패. 컨트롤러에 닿기 전에 나므로 전역에서만 잡힌다.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException() {
        return createErrorResponse(CommonErrorCode.INVALID_FILE_FORMAT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("[UnexpectedException]", exception);
        return createErrorResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> createErrorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(errorCode));
    }
}
