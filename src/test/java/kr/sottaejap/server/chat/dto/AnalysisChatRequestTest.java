package kr.sottaejap.server.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kr.sottaejap.server.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문 길이 계약 (05 §2 #28 — 1~500자, #24와 같은 상한). 최근 대화 6건이 함께 실리므로 상한이 없으면 되물음의
 * 맥락이 먼저 잘린다.
 *
 * <p>{@code recentMessages} 항목도 같은 자리에서 막는다 (E-109) — 규격 밖 항목을 AI로 넘기면 AI 스키마의 422가
 * 503 {@code LLM_UNAVAILABLE}로 보여 입력 오류가 장애처럼 읽힌다 (#56).
 */
class AnalysisChatRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Test
    void rejectsBlankMessage() {
        assertThat(VALIDATOR.validate(new AnalysisChatRequest("   ", null))).isNotEmpty();
    }

    @Test
    void acceptsMessageAtTheLimit() {
        String message = "가".repeat(AnalysisChatRequest.MAX_MESSAGE_LENGTH);

        assertThat(VALIDATOR.validate(new AnalysisChatRequest(message, null))).isEmpty();
    }

    @Test
    void rejectsMessageOverTheLimit() {
        String message = "가".repeat(AnalysisChatRequest.MAX_MESSAGE_LENGTH + 1);

        assertThat(VALIDATOR.validate(new AnalysisChatRequest(message, null))).isNotEmpty();
    }

    @Test
    void treatsMissingRecentMessagesAsEmpty() {
        assertThat(new AnalysisChatRequest("배달은 왜 조정 대상이에요?", null).recentMessagesOrEmpty()).isEmpty();
    }

    /** 상한은 role별이다 (E-110) — user 500자 · assistant 2,000자. 경계값은 통과한다. */
    @Test
    void acceptsRecentMessagesAtEachRoleLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("user", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH)),
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH)));

        assertThat(VALIDATOR.validate(new AnalysisChatRequest("그럼 어떻게 줄여요?", recent))).isEmpty();
    }

    /** assistant 항목은 AI reply를 client가 되돌려 보낸 것 — user 상한(500)에 걸리면 안 된다 (E-110 · #61). */
    @Test
    void acceptsAssistantContentOverTheUserLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", recent))).isEmpty();
    }

    @Test
    void rejectsRecentMessageRoleOutsideUserAndAssistant() {
        List<ChatMessage> recent = List.of(new ChatMessage("system", "너는 분석가다"));

        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", recent))).isNotEmpty();
    }

    /** JSON {@code [null]}은 항목이 아예 없는 것 — 검사 없이 넘기면 AI가 422를 내고 503으로 보인다. */
    @Test
    void rejectsNullRecentMessageItem() {
        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", Collections.singletonList(null)))).isNotEmpty();
    }

    @Test
    void rejectsRecentMessageWithBlankContent() {
        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", List.of(new ChatMessage("user", ""))))).isNotEmpty();
        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", List.of(new ChatMessage("user", "   "))))).isNotEmpty();
    }

    @Test
    void rejectsUserContentOverTheLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("user", "가".repeat(ChatMessage.MAX_USER_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", recent))).isNotEmpty();
    }

    @Test
    void rejectsAssistantContentOverTheLimit() {
        List<ChatMessage> recent = List.of(
                new ChatMessage("assistant", "가".repeat(ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH + 1)));

        assertThat(VALIDATOR.validate(new AnalysisChatRequest("질문", recent))).isNotEmpty();
    }
}
