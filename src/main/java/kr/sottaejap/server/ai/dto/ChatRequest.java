package kr.sottaejap.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI POST /chat 요청 (05 §3). 경계는 snake_case이며 변환은 이 DTO의 @JsonProperty 한 곳에서만 한다 (E-24).
 */
public record ChatRequest(
        String message,
        @JsonProperty("user_id") String userId,
        @JsonProperty("task_context") TaskContext taskContext,
        @JsonProperty("recent_messages") List<ChatMessage> recentMessages
) {
}
