package kr.sottaejap.server.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 브라우저 `PushSubscription.toJSON()`을 그대로 받는다 — 클라이언트가 모양을 다시 만들지 않아도 되게 한다.
 * `expirationTime`은 쓰지 않으므로 받지 않는다 (모르는 필드는 Jackson이 무시한다).
 */
public record PushSubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
