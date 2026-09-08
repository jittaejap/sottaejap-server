package kr.sottaejap.server.chat.dto;

import kr.sottaejap.server.ai.dto.ChatResponse;

/**
 * POST /chat/finance 응답.
 *
 * <p>출처는 별도 필드로 두지 않는다 — 문장 안에서 느슨하게 언급한다 (E-47). 정확한 문서명·연도를
 * 필드로 요구하면 LLM이 그것을 지어내기 때문이다.
 *
 * @param fallback AI에 OPENAI_API_KEY가 없어 템플릿으로 답했을 때 true. 클라이언트가 템플릿 모드 배너를
 *                 띄운다 (S11 · E-38). LLM 장애는 여기 오지 않는다 — 타임아웃·5xx는 AiClient가 503
 *                 `LLM_UNAVAILABLE`로 던지므로 응답 자체가 나가지 않는다 (05 §2)
 */
public record FinanceChatResponse(String reply, boolean fallback) {

    public static FinanceChatResponse from(ChatResponse response) {
        return new FinanceChatResponse(response.reply(), response.isFallback());
    }
}
