package kr.sottaejap.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import kr.sottaejap.server.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 모든 JSON API의 공통 봉투 (05 §0).
 *
 * 성공이면 data만, 실패면 error만 포함한다. 클라이언트는 error.message가 아니라 error.code로 분기한다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;

    private ApiResponse(boolean success, T data, ApiError error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * HTTP 상태는 GlobalExceptionHandler가 ErrorCode에서 정한다.
     */
    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ApiError.from(errorCode));
    }
}
