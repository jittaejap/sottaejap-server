package kr.sottaejap.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * AI POST /chat 응답 (05 §3).
 *
 * @param fallback v1.3 추가 필드. AI 레포가 아직 내려주지 않으면(06 R3 전) null이다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(
        String reply,
        @JsonProperty("tool_results") List<Map<String, Object>> toolResults,
        @JsonProperty("needs_clarification") Boolean needsClarification,
        Boolean fallback
) {

    public boolean isFallback() {
        return Boolean.TRUE.equals(fallback);
    }
}
