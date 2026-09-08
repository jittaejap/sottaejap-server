package kr.sottaejap.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 브라우저 Push 구독 한 건. 스키마 정본은 db/migration/V5__push_subscriptions.sql이다.
 *
 * <p>{@code endpoint}는 브라우저가 만든 URL이고 구독을 식별한다. {@code p256dh}·{@code auth}는
 * 본문 암호화 키라서 서버는 해석하지 않고 그대로 보관했다가 발송할 때 넘긴다.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private PushSubscription(Long userId, String endpoint, String p256dh, String auth, OffsetDateTime createdAt) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    public static PushSubscription of(long userId, String endpoint, String p256dh, String auth,
                                      OffsetDateTime createdAt) {
        return new PushSubscription(userId, endpoint, p256dh, auth, createdAt);
    }

    /** 같은 브라우저가 키를 새로 만들어 다시 구독하면 행을 늘리지 않고 키만 갈아 끼운다. */
    public void replaceKeys(long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
