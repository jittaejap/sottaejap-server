package kr.sottaejap.server.common.exception;

import lombok.Getter;

/**
 * Service에서 일어난 비즈니스 오류를 GlobalExceptionHandler까지 전달한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
