package kr.sottaejap.server.ai;

import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.enums.TaskType;
import org.junit.jupiter.api.Test;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
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
     * <b>숫자 값</b>을 허용 목록으로 만든다 (ai `analysis_narrate._known_numbers`) — 집계 record가
     * 중첩 객체로 직렬화되지 않으면 그 목록이 비고, 멀쩡한 문장이 전부 폴백으로 떨어진다.
     */
    @Test
    void narrateState_carriesSummaryObjectsAndOmitsPending() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("analysis_year_month", "2026-08");
        state.put("by_verdict", List.of(new VerdictSummary(Verdict.ADJUST, 1, 36000, 0.036)));
        state.put("by_category", List.of(new CategorySummary("배달", TimeSlot.NIGHT, 12000, 36000, Verdict.ADJUST)));

        String json = mapper.writeValueAsString(new ChatRequest("특징", "1",
                new TaskContext(TaskType.ANALYSIS_NARRATE, RetrospectStatus.ACTIVE, state), List.of()));

        assertTrue(json.contains("\"task\":\"ANALYSIS_NARRATE\""), json);
        assertTrue(json.contains("\"analysis_year_month\":\"2026-08\""), json);
        assertTrue(json.contains("\"by_verdict\":[{\"verdict\":\"ADJUST\",\"clusterCount\":1,"
                + "\"monthlyTotalAmount\":36000,\"share\":0.036}]"), json);
        assertTrue(json.contains("\"by_category\":[{\"category\":\"배달\",\"dominantTimeSlot\":\"NIGHT\","
                + "\"avgAmount\":12000,\"monthlyTotalAmount\":36000,\"verdict\":\"ADJUST\"}]"), json);
        assertFalse(json.contains("pending"), json);
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
