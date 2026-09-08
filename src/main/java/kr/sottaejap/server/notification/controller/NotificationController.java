package kr.sottaejap.server.notification.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.notification.WebPushProperties;
import kr.sottaejap.server.notification.dto.NotificationListResponse;
import kr.sottaejap.server.notification.dto.PushSubscriptionRequest;
import kr.sottaejap.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final WebPushProperties webPushProperties;

    @GetMapping
    public ApiResponse<NotificationListResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(notificationService.findForUser(user.userId()));
    }

    /** 읽음 처리가 곧 "지금은 말고"다 — 후보 제외 상태를 따로 저장하지 않는다 (E-49). */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        notificationService.markAsRead(user.userId(), id);
        return ApiResponse.success();
    }

    /**
     * 클라이언트가 `pushManager.subscribe`에 넘길 applicationServerKey.
     *
     * <p>VAPID 공개키는 이름 그대로 공개용이라 내려줘도 된다. {@code enabled}가 false면 서버에 키가
     * 없다는 뜻이므로 클라이언트는 구독을 시도하지 말고 인앱 알림만 쓴다.
     */
    @GetMapping("/push-key")
    public ApiResponse<Map<String, Object>> pushKey() {
        boolean enabled = webPushProperties.isConfigured();
        return ApiResponse.success(Map.of(
                "enabled", enabled,
                "publicKey", enabled ? webPushProperties.publicKey() : ""));
    }

    /** 브라우저 `PushSubscription.toJSON()`을 그대로 받는다. 같은 endpoint면 키만 갱신한다. */
    @PostMapping("/push-subscriptions")
    public ApiResponse<Void> subscribe(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestBody @Valid PushSubscriptionRequest request) {
        notificationService.subscribeToPush(user.userId(), request);
        return ApiResponse.success();
    }

    @DeleteMapping("/push-subscriptions")
    public ApiResponse<Void> unsubscribe(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam String endpoint) {
        notificationService.unsubscribeFromPush(user.userId(), endpoint);
        return ApiResponse.success();
    }
}
