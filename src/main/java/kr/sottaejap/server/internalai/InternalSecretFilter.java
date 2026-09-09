package kr.sottaejap.server.internalai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.sottaejap.server.ai.AiProperties;
import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.auth.security.SecurityErrorResponseWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * `/internal/ai/**`는 AI 서버만 부른다. X-Internal-Secret이 AI_SHARED_SECRET과 다르면 401.
 * 개발 확인용 `/internal-test/**`도 같은 규칙이다 — 운영 프록시가 `/internal` 접두사를 막는다는 전제가
 * 실제와 달라 인증 없이 열려 있었다 (#75).
 * 시크릿이 비어 있으면 어떤 요청도 통과시키지 않는다 — 설정 실수로 열리는 것을 막는다.
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Secret";
    private static final List<String> PATH_PREFIXES = List.of("/internal/ai/", "/internal-test/");

    private final byte[] expectedSecret;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public InternalSecretFilter(AiProperties properties, SecurityErrorResponseWriter errorResponseWriter) {
        String secret = properties.sharedSecret();
        this.expectedSecret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PATH_PREFIXES.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedSecret == null || provided == null
                || !MessageDigest.isEqual(expectedSecret, provided.getBytes(StandardCharsets.UTF_8))) {
            errorResponseWriter.write(response, AuthErrorCode.UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
