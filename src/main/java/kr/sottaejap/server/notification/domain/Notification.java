package kr.sottaejap.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.NotificationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 04 §1 Notification. 스키마 정본은 db/migration이다 (ddl-auto=validate).
 *
 * <p>{@code refId}는 종류에 따라 가리키는 대상이 다르다 — RETROSPECT_DUE는 거래 id, SUGGESTION은 제안 id다.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "ref_id")
    private Long refId;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private Notification(Long userId, NotificationType type, Long refId, String message, OffsetDateTime createdAt) {
        this.userId = userId;
        this.type = type;
        this.refId = refId;
        this.message = message;
        this.isRead = false;
        this.createdAt = createdAt;
    }

    public static Notification of(long userId, NotificationType type, Long refId, String message,
                                  OffsetDateTime createdAt) {
        return new Notification(userId, type, refId, message, createdAt);
    }

    /**
     * 읽음 처리는 후보 제외(skip)를 겸한다 — 별도 상태를 저장하지 않는다 (E-49).
     * 사용자는 같은 거래를 채팅·거래내역에서 다시 회고할 수 있어야 하기 때문이다.
     */
    public void markAsRead() {
        this.isRead = true;
    }
}
