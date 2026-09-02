package kr.sottaejap.server.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 07 §3 — AI_BASE_URL · AI_SHARED_SECRET · AI_TIMEOUT_MS.
 *
 * @param timeoutMs Spring → AI /chat 전체 타임아웃. AI 내부의 LLM 8초 + Spring 조회 왕복을 포함해야 한다 (07 §10 리스크 4)
 */
@ConfigurationProperties("ai")
public record AiProperties(String baseUrl, String sharedSecret, long timeoutMs) {
}
