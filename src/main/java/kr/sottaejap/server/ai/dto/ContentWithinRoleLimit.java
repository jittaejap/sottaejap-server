package kr.sottaejap.server.ai.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ChatMessage}의 {@code content} 상한이 role에 따라 다르다는 제약 (E-110). {@code user} 500자 · {@code assistant} 2,000자.
 * role이 규격 밖이거나 content가 비어 있으면 필드 제약이 따로 보고하므로 여기서는 통과시킨다 — 한 위반을 두 번 세지 않는다.
 */
@Documented
@Constraint(validatedBy = ContentWithinRoleLimitValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContentWithinRoleLimit {

    String message() default "content가 role별 상한을 넘습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
