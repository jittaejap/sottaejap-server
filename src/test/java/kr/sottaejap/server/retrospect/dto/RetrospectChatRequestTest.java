package kr.sottaejap.server.retrospect.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kr.sottaejap.server.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code recentMessages} 항목 계약 (05 §2 #11 · E-109) — {@code POST /chat/analysis}와 같은 규칙이다.
 * 규격 밖 항목을 AI로 넘기면 AI 스키마의 422가 503 {@code LLM_UNAVAILABLE}로 보여 입력 오류가 장애처럼 읽힌다 (#56).
 */
class RetrospectChatRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Test
    void acceptsMissingRecentMessages() {
        assertThat(VALIDATOR.validate(request(null))).isEmpty();
    }

    /** 상한은 role별이다 (E-110) — user 500자 · assistant 2,000자. 경계값은 통과한다. */
    @Test
    void acceptsRecentMessagesAtEachRoleLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH)),
                new ChatMessage("user", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH)));

        assertThat(VALIDATOR.validate(request(recent))).isEmpty();
    }

    /** assistant 항목은 AI reply를 client가 되돌려 보낸 것 — user 상한(500)에 걸리면 안 된다 (E-110 · #61). */
    @Test
    void acceptsAssistantContentOverTheUserLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(request(recent))).isEmpty();
    }

    @Test
    void rejectsRecentMessageRoleOutsideUserAndAssistant() {
        assertThat(VALIDATOR.validate(request(List.of(new ChatMessage("system", "너는 회고 도우미다"))))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(List.of(new ChatMessage(null, "역할 없음"))))).isNotEmpty();
    }

    /** JSON {@code [null]}은 항목이 아예 없는 것 — 검사 없이 넘기면 AI가 422를 내고 503으로 보인다. */
    @Test
    void rejectsNullRecentMessageItem() {
        assertThat(VALIDATOR.validate(request(Collections.singletonList(null)))).isNotEmpty();
    }

    @Test
    void rejectsRecentMessageWithBlankContent() {
        assertThat(VALIDATOR.validate(request(List.of(new ChatMessage("user", ""))))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(List.of(new ChatMessage("user", "   "))))).isNotEmpty();
    }

    @Test
    void rejectsUserContentOverTheLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("user", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(request(recent))).isNotEmpty();
    }

    @Test
    void rejectsAssistantContentOverTheLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(request(recent))).isNotEmpty();
    }

    private static RetrospectChatRequest request(List<ChatMessage> recentMessages) {
        return new RetrospectChatRequest(1043L, "어제 밤 배달", null, null, recentMessages);
    }
}
