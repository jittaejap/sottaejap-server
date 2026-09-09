package kr.sottaejap.server.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.sottaejap.server.ai.dto.ChatMessage;

import java.util.List;

/**
 * POST /chat/analysis 요청 (05 #28 · E-104 · FR-11-05). 상태 없는 프록시 — 클라이언트가 소비 분석 채널의
 * 최근 대화를 들고 다닌다 ({@code POST /retrospects/chat}과 같은 모양, E-63).
 *
 * <p>금융 Q&A(#24)와 달리 서버가 대화를 저장하지 않는다. {@code chat_messages}는 {@code transaction_id IS NULL}을
 * 금융 Q&A의 맥락으로 쓰고 있어(E-67) 여기 섞으면 두 채널의 되물음이 서로 오염된다.
 *
 * @param message        1~500자 — #24와 같은 상한, 같은 이유(프롬프트 예산). 최근 대화 6건이 함께 실린다
 * @param recentMessages 오름차순(오래된 → 최신). 생략 가능. 서버는 최근 6개만 AI에 넘긴다 (E-87). 항목은 {@link ChatMessage}의
 *                       제약(role · 1~500자)을 어기면 400 INVALID_INPUT — AI를 부르지 않는다 (E-109)
 */
public record AnalysisChatRequest(
        @NotBlank @Size(max = MAX_MESSAGE_LENGTH) String message,
        List<@NotNull @Valid ChatMessage> recentMessages
) {

    static final int MAX_MESSAGE_LENGTH = 500;

    public List<ChatMessage> recentMessagesOrEmpty() {
        return recentMessages == null ? List.of() : recentMessages;
    }
}
