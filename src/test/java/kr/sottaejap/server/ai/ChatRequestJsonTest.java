package kr.sottaejap.server.ai;

import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;

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

    /**
     * ANALYSIS_NARRATE state (05 §3 · E-75). AI의 숫자 가드는 `by_verdict`·`by_category` 항목의
     * <b>숫자 값</b>을 허용 목록으로 만든다 (ai `analysis_narrate._known_numbers`) — 그 항목이
     * 중첩 객체로 직렬화되지 않으면 그 목록이 비고, 멀쩡한 문장이 전부 폴백으로 떨어진다.
     *
     * <p>키는 중첩 객체까지 snake_case다. state를 만드는 곳은 {@code HighlightServiceImpl.state}이고,
     * 여기서는 그 모양이 경계에서 그대로 나가는지만 본다.
     */
    @Test
    void narrateState_carriesSnakeCaseSummaryObjectsAndOmitsPending() {
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("verdict", "ADJUST");
        verdict.put("cluster_count", 1);
        verdict.put("monthly_total_amount", 36000);
        verdict.put("share", 0.036);

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("category", "배달");
        category.put("dominant_time_slot", "NIGHT");
        category.put("avg_amount", 12000);
        category.put("monthly_total_amount", 36000);
        category.put("verdict", "ADJUST");

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("analysis_year_month", "2026-08");
        state.put("by_verdict", List.of(verdict));
        state.put("by_category", List.of(category));

        String json = mapper.writeValueAsString(new ChatRequest("특징", "1",
                new TaskContext(TaskType.ANALYSIS_NARRATE, RetrospectStatus.ACTIVE, state), List.of()));

        assertTrue(json.contains("\"task\":\"ANALYSIS_NARRATE\""), json);
        assertTrue(json.contains("\"analysis_year_month\":\"2026-08\""), json);
        assertTrue(json.contains("\"by_verdict\":[{\"verdict\":\"ADJUST\",\"cluster_count\":1,"
                + "\"monthly_total_amount\":36000,\"share\":0.036}]"), json);
        assertTrue(json.contains("\"by_category\":[{\"category\":\"배달\",\"dominant_time_slot\":\"NIGHT\","
                + "\"avg_amount\":12000,\"monthly_total_amount\":36000,\"verdict\":\"ADJUST\"}]"), json);
        assertFalse(json.contains("pending"), json);
        assertFalse(json.contains("monthlyTotalAmount"), json);
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
