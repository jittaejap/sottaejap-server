package kr.sottaejap.server.notification.service;

import kr.sottaejap.server.notification.dto.NotificationListResponse;
import kr.sottaejap.server.notification.dto.PushSubscriptionRequest;

public interface NotificationService {

    NotificationListResponse findForUser(long userId);

    void markAsRead(long userId, long notificationId);

    void subscribeToPush(long userId, PushSubscriptionRequest request);

    void unsubscribeFromPush(long userId, String endpoint);
}
