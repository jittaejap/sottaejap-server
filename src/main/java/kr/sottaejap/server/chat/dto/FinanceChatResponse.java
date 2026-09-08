package kr.sottaejap.server.chat.dto;

import kr.sottaejap.server.ai.dto.ChatResponse;

/**
 * POST /chat/finance 응답.
 *
 * <p>출처는 별도 필드로 두지 않는다 — 문장 안에서 느슨하게 언급한다 (E-47). 정확한 문서명·연도를
 * 필드로 요구하면 LLM이 그것을 지어내기 때문이다.
 *
 * @param fallback **AI가 템플릿으로 답했을 때** true. 클라이언트가 템플릿 모드 배너를 띄운다 (S11).
 *                 두 경우다 — `OPENAI_API_KEY` 미설정 (E-38) · AI → OpenAI가 6초를 넘기거나 실패
 *                 (E-88 · 재시도 1회 뒤 템플릿, HTTP는 200 유지). **Spring → AI 왕복 자체가 실패하면
 *                 여기 오지 않는다** — 타임아웃·5xx는 `AiClient`가 503 `LLM_UNAVAILABLE`로 던지므로
 *                 응답이 나가지 않는다 (05 §0 · §3)
 */
public record FinanceChatResponse(String reply, boolean fallback) {

    public static FinanceChatResponse from(ChatResponse response) {
        return new FinanceChatResponse(response.reply(), response.isFallback());
    }
}
