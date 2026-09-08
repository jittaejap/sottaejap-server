package kr.sottaejap.server.notification.dto;

import kr.sottaejap.server.common.enums.NotificationType;
import kr.sottaejap.server.notification.domain.Notification;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /notifications (05 §2). 필드와 이름은 문서 예시와 같다.
 */
public record NotificationListResponse(long unreadCount, List<Item> notifications) {

    public record Item(Long id,
                       NotificationType type,
                       Long refId,
                       String message,
                       boolean isRead,
                       OffsetDateTime createdAt) {

        public static Item from(Notification notification) {
            return new Item(
                    notification.getId(),
                    notification.getType(),
                    notification.getRefId(),
                    notification.getMessage(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
