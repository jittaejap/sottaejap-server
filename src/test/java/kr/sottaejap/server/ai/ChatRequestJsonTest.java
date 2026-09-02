package kr.sottaejap.server.ai;

import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring ↔ AI /chat 경계는 snake_case (E-24). 변환은 DTO 한 곳에서만 한다.
 */
class ChatRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void request_serializesAsSnakeCase() {
        ChatRequest request = new ChatRequest(
                "ping", "1",
                new TaskContext(TaskType.REFLECTION, RetrospectStatus.ACTIVE, Map.of("step", "INTRO")),
                List.of(new ChatMessage("assistant", "안녕하세요")));

        String json = mapper.writeValueAsString(request);

        assertTrue(json.contains("\"user_id\":\"1\""), json);
        assertTrue(json.contains("\"task_context\":{\"task\":\"REFLECTION\",\"status\":\"ACTIVE\""), json);
        assertTrue(json.contains("\"recent_messages\":[{\"role\":\"assistant\""), json);
        assertFalse(json.contains("userId"), json);
    }

    @Test
    void response_toleratesMissingFallbackAndUnknownFields() {
        String json = """
                {"reply":"ok","tool_results":[],"needs_clarification":false,"future_field":1}
                """;

        ChatResponse response = mapper.readValue(json, ChatResponse.class);

        assertEquals("ok", response.reply());
        assertFalse(response.isFallback());
    }
}
