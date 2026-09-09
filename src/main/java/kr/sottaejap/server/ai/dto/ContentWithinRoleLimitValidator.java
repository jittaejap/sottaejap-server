package kr.sottaejap.server.ai.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ContentWithinRoleLimit} 구현. 상한 값은 {@link ChatMessage} 상수가 정본이다. */
public class ContentWithinRoleLimitValidator implements ConstraintValidator<ContentWithinRoleLimit, ChatMessage> {

    @Override
    public boolean isValid(ChatMessage message, ConstraintValidatorContext context) {
        if (message == null || message.content() == null) {
            return true;
        }
        int length = message.content().length();
        if (ChatMessage.USER.equals(message.role())) {
            return length <= ChatMessage.MAX_USER_CONTENT_LENGTH;
        }
        if (ChatMessage.ASSISTANT.equals(message.role())) {
            return length <= ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH;
        }
        return true;
    }
}
