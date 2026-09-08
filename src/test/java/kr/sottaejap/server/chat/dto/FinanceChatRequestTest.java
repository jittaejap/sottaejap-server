package kr.sottaejap.server.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문 길이 계약 (05 §2 v2.10 — 1~500자). 상한은 프롬프트 예산에서 나온 값이다 — 같은 요청에 최근 대화
 * 6건이 함께 실리므로(05 §3), 상한이 없으면 붙여넣기 한 번에 되물음의 맥락이 먼저 잘린다.
 */
class FinanceChatRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Test
    void rejectsBlankMessage() {
        assertThat(VALIDATOR.validate(new FinanceChatRequest("   "))).isNotEmpty();
    }

    @Test
    void acceptsMessageAtTheLimit() {
        String message = "가".repeat(FinanceChatRequest.MAX_MESSAGE_LENGTH);

        assertThat(VALIDATOR.validate(new FinanceChatRequest(message))).isEmpty();
    }

    @Test
    void rejectsMessageOverTheLimit() {
        String message = "가".repeat(FinanceChatRequest.MAX_MESSAGE_LENGTH + 1);

        assertThat(VALIDATOR.validate(new FinanceChatRequest(message))).isNotEmpty();
    }
}
