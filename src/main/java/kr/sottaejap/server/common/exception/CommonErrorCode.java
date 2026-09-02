package kr.sottaejap.server.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 05 §0 오류 코드표. 코드 문자열은 문서와 글자 단위로 같아야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_FILE_FORMAT", "지원하지 않는 파일 형식이에요."),
    PARSE_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "PARSE_FAILED", "거래내역을 읽지 못했어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 정보를 찾을 수 없어요."),
    DUPLICATE_RETROSPECT(HttpStatus.CONFLICT, "DUPLICATE_RETROSPECT", "이 거래는 이미 돌아봤어요."),
    ONBOARDING_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "ONBOARDING_REQUIRED", "온보딩을 먼저 마쳐 주세요."),
    INVALID_TAG(HttpStatus.BAD_REQUEST, "INVALID_TAG", "목적과 동행인은 정해진 선택지 중에서 골라 주세요."),
    LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", "AI 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."),

    // 05 §0에 v1.5(서버 스캐폴딩)로 추가한 공통 코드
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값을 확인해 주세요."),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "아직 준비 중인 기능이에요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "문제가 생겼어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
