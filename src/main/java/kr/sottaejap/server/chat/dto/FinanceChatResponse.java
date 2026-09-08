package kr.sottaejap.server.chat.dto;

import kr.sottaejap.server.ai.dto.ChatResponse;

/**
 * POST /chat/finance 응답.
 *
 * <p>출처는 별도 필드로 두지 않는다 — 문장 안에서 느슨하게 언급한다 (E-47). 정확한 문서명·연도를
 * 필드로 요구하면 LLM이 그것을 지어내기 때문이다.
 *
 * @param fallback LLM 장애·키 미설정 시 true. 클라이언트가 템플릿 모드 배너를 띄운다 (S11 · E-38)
 */
public record FinanceChatResponse(String reply, boolean fallback) {

    public static FinanceChatResponse from(ChatResponse response) {
        return new FinanceChatResponse(response.reply(), response.isFallback());
    }
}
