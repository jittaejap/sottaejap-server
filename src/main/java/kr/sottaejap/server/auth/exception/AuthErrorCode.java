package kr.sottaejap.server.auth.exception;

import kr.sottaejap.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 오류 코드. 05 §0에 v1.5(서버 스캐폴딩)로 추가했고, OAUTH_* 2종은 v2.1(E-55)이다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없어요."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "지원하지 않는 로그인 방식이에요."),
    DEMO_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "DEMO_ACCOUNT_DISABLED", "데모 계정 로그인이 꺼져 있어요."),
    OAUTH_CODE_INVALID(HttpStatus.BAD_REQUEST, "OAUTH_CODE_INVALID", "카카오 로그인 코드가 유효하지 않아요. 다시 로그인해 주세요."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "OAUTH_PROVIDER_ERROR", "카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
