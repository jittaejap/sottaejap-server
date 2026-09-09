package kr.sottaejap.server.retrospect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.common.enums.ReflectionStep;

import java.util.List;

/**
 * POST /retrospects/chat 본문 (05 §2 · E-63). 상태 없는 프록시 — 클라이언트가 step과 확정값을 들고 다닌다.
 *
 * @param message        INTRO에서는 생략 가능 (서버가 고정 문구로 대체)
 * @param step           생략 시 INTRO
 * @param reflection     사용자가 이미 확인한 값. 생략 시 전부 미확정
 * @param recentMessages 최근 대화. 서버는 최근 6개만 넘긴다 (E-87). 항목은 {@link ChatMessage}의 제약(role · user 500자 · assistant 2,000자)을
 *                       어기면 400 INVALID_INPUT — AI를 부르지 않는다 (E-109)
 */
public record RetrospectChatRequest(
        @NotNull Long transactionId,
        String message,
        ReflectionStep step,
        ReflectionDraft reflection,
        List<@NotNull @Valid ChatMessage> recentMessages
) {

    public ReflectionStep stepOrDefault() {
        return step == null ? ReflectionStep.INTRO : step;
    }

    public ReflectionDraft reflectionOrDefault() {
        return reflection == null ? ReflectionDraft.empty() : reflection;
    }

    public List<ChatMessage> recentMessagesOrEmpty() {
        return recentMessages == null ? List.of() : recentMessages;
    }
}
