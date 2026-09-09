package kr.sottaejap.server.internalai;

import kr.sottaejap.server.ai.AiProperties;
import kr.sottaejap.server.auth.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기계가 부르는 두 접두사(`/internal/ai/` · `/internal-test/`)만 X-Internal-Secret을 요구한다 (#75).
 * `/internal-test/ai-ping`은 운영 프록시가 막아 줄 것으로 보고 열어 뒀다가 무인증 200이었다.
 */
class InternalSecretFilterTest {

    private static final String SECRET = "shared-secret";

    private final SecurityErrorResponseWriter errorResponseWriter =
            new SecurityErrorResponseWriter(JsonMapper.builder().build());

    private InternalSecretFilter filter(String secret) {
        return new InternalSecretFilter(new AiProperties("http://localhost:8000", secret, 15000L), errorResponseWriter);
    }

    private MockFilterChain call(InternalSecretFilter filter, String uri, String header,
                                 MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (header != null) {
            request.addHeader(InternalSecretFilter.HEADER, header);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    void ai_ping은_헤더가_없으면_401_봉투를_돌려준다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call(filter(SECRET), "/internal-test/ai-ping", null, response);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"UNAUTHORIZED\""));
        assertNull(chain.getRequest(), "필터가 막았으면 컨트롤러까지 가지 않는다");
    }

    @Test
    void ai_ping은_맞는_헤더면_통과한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call(filter(SECRET), "/internal-test/ai-ping", SECRET, response);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "필터를 통과하면 다음 체인이 돈다");
    }

    @Test
    void ai_ping은_틀린_헤더면_401이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call(filter(SECRET), "/internal-test/ai-ping", "wrong", response);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void internal_ai_경로는_기존_동작_그대로다() throws Exception {
        MockHttpServletResponse denied = new MockHttpServletResponse();
        MockFilterChain deniedChain = call(filter(SECRET), "/internal/ai/users/1/memory", null, denied);

        assertEquals(401, denied.getStatus());
        assertNull(deniedChain.getRequest());

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        MockFilterChain allowedChain = call(filter(SECRET), "/internal/ai/users/1/memory", SECRET, allowed);

        assertEquals(200, allowed.getStatus());
        assertNotNull(allowedChain.getRequest());
    }

    @Test
    void 공개_경로는_필터를_타지_않는다() throws Exception {
        for (String uri : new String[]{"/actuator/health", "/auth/login"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            MockFilterChain chain = call(filter(SECRET), uri, null, response);

            assertEquals(200, response.getStatus(), uri);
            assertNotNull(chain.getRequest(), uri);
        }
    }

    @Test
    void 시크릿이_비어_있으면_맞는_헤더라도_401이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call(filter("  "), "/internal-test/ai-ping", SECRET, response);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }
}
