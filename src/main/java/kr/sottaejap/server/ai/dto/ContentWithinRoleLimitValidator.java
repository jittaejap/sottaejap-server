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
        // 코드 포인트로 센다 — String.length()는 UTF-16 단위라 이모지가 2로 세어, ai가 규격(코드 포인트 2,000)을 지켜도
        // 서버가 400을 낸다. ClusterNameTemplate.truncate(12자 · 이슈 #20)와 같은 셈법이다.
        int length = message.content().codePointCount(0, message.content().length());
        if (ChatMessage.USER.equals(message.role())) {
            return length <= ChatMessage.MAX_USER_CONTENT_LENGTH;
        }
        if (ChatMessage.ASSISTANT.equals(message.role())) {
            return length <= ChatMessage.MAX_ASSISTANT_CONTENT_LENGTH;
        }
        return true;
    }
}
