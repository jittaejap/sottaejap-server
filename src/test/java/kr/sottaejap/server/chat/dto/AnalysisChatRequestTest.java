package kr.sottaejap.server.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 질문 길이 계약 (05 §2 #28 — 1~500자, #24와 같은 상한). 최근 대화 6건이 함께 실리므로 상한이 없으면 되물음의
 * 맥락이 먼저 잘린다.
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
}
