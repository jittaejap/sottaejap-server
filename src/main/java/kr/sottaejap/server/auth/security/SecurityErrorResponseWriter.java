package kr.sottaejap.server.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import kr.sottaejap.server.common.exception.ErrorCode;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터 단계(컨트롤러 밖)에서도 공통 봉투로 실패 응답을 쓴다. Boot 4는 Jackson 3(tools.jackson)이다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(errorCode)));
    }
}
