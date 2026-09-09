package kr.sottaejap.server.internalai;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 07 §4 — `curl -H "X-Internal-Secret: $AI_SHARED_SECRET" localhost:8080/internal-test/ai-ping` 이 200을
 * 돌려주면 Spring → AI /chat 왕복이 산다. 개발 확인용이고, 헤더가 없으면 InternalSecretFilter가 401로 막는다 (#75).
 */
@RestController
@RequiredArgsConstructor
public class AiPingController {

    private final AiClient aiClient;

    @GetMapping("/internal-test/ai-ping")
    public ApiResponse<AiPingResponse> ping() {
        ChatRequest request = new ChatRequest(
                "ping",
                "1",
                new TaskContext(TaskType.REFLECTION, RetrospectStatus.ACTIVE, Map.of("step", "INTRO")),
                List.of()
        );
        ChatResponse response = aiClient.chat(request);
        return ApiResponse.success(new AiPingResponse(response.reply(), response.isFallback()));
    }

    public record AiPingResponse(String reply, boolean fallback) {
    }
}
