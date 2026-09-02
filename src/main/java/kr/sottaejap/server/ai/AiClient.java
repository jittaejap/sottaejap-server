package kr.sottaejap.server.ai;

import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring이 AI 서버를 부르는 유일한 지점 (E-19). 경로는 POST /chat 하나이며 작업 종류는 task_context.task로 구분한다.
 *
 * <p>타임아웃·5xx는 503 LLM_UNAVAILABLE로 바꾼다. 클라이언트는 그 코드를 보고 템플릿 모드로 전환한다 (05 §0).
 */
@Component
public class AiClient {

    static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient restClient;

    public AiClient(AiProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        // JDK HttpClient는 기본으로 HTTP/2 업그레이드(h2c) 헤더를 보내고, uvicorn(h11)은 그 요청의 본문을 버려
        // 422 "body missing"이 난다. AI 서버와는 HTTP/1.1로만 통신한다.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_SECRET_HEADER, properties.sharedSecret() == null ? "" : properties.sharedSecret())
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        try {
            ChatResponse response = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.reply() == null) {
                throw new BusinessException(CommonErrorCode.LLM_UNAVAILABLE);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessException(CommonErrorCode.LLM_UNAVAILABLE, exception);
        }
    }
}
