package kr.sottaejap.server.chat.dto;

import kr.sottaejap.server.ai.dto.ChatResponse;

/**
 * POST /chat/analysis 응답 (05 #28). #24와 같은 모양이다.
 *
 * @param fallback AI가 템플릿으로 답했을 때 true — {@code OPENAI_API_KEY} 미설정(E-38) · AI → OpenAI 초과·실패(E-88).
 *                 유효 묶음이 없을 때의 안내문은 정상 응답이라 false다. Spring → AI 왕복 자체가 실패하면 여기 오지
 *                 않는다 — {@code AiClient}가 503 {@code LLM_UNAVAILABLE}로 던진다
 */
public record AnalysisChatResponse(String reply, boolean fallback) {

    public static AnalysisChatResponse from(ChatResponse response) {
        return new AnalysisChatResponse(response.reply(), response.isFallback());
    }
}
