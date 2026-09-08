package kr.sottaejap.server.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 회고 대화 한 줄. 스키마 정본은 db/migration/V6__chat_messages.sql이다.
 *
 * <p>AI에 보내는 `recent_messages`의 재료다. 계산도 판정도 하지 않고 오간 말만 그대로 남긴다.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    /** AI 경계에서는 소문자 문자열이다 (05 §3 `recent_messages[].role`). */
    public enum Role {
        USER, ASSISTANT;

        public String toAiRole() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 거래에 매이지 않는 대화는 null이다. */
    @Column(name = "transaction_id")
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private ChatMessage(Long userId, Long transactionId, Role role, String content, OffsetDateTime createdAt) {
        this.userId = userId;
        this.transactionId = transactionId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ChatMessage of(long userId, Long transactionId, Role role, String content,
                                 OffsetDateTime createdAt) {
        return new ChatMessage(userId, transactionId, role, content, createdAt);
    }
}
