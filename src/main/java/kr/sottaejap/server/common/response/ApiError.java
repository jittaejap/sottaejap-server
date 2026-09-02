package kr.sottaejap.server.common.response;

import kr.sottaejap.server.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ApiError {

    private final String code;
    private final String message;

    private ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ApiError from(ErrorCode errorCode) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage());
    }
}
