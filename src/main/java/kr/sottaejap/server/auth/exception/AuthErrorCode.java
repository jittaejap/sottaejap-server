package kr.sottaejap.server.auth.exception;

import kr.sottaejap.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 오류 코드. 05 §0에 v1.5(서버 스캐폴딩)로 추가했다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없어요."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "지원하지 않는 로그인 방식이에요."),
    DEMO_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "DEMO_ACCOUNT_DISABLED", "데모 계정 로그인이 꺼져 있어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
