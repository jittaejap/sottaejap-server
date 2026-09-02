package kr.sottaejap.server.ai.dto;

/**
 * @param role "user" 또는 "assistant"
 */
public record ChatMessage(String role, String content) {
}
