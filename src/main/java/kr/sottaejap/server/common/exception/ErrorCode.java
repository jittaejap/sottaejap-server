package kr.sottaejap.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 계약. 문자열 코드 목록의 정본은 myDocs/05_API_명세서.md §0이다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    /**
     * 클라이언트가 분기하는 고유 코드. 바꾸면 화면이 깨지므로 05 문서와 같은 PR에서 고친다.
     */
    String getCode();

    /**
     * 사용자에게 보여도 되는 문구. 내부 예외 메시지·SQL·개인정보를 넣지 않는다.
     */
    String getMessage();
}
