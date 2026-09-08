package kr.sottaejap.server.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.TimeSlot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 04 §1 Transaction. 스키마 정본은 db/migration이다 (ddl-auto=validate).
 *
 * <p>merchantNormalized는 아직 채우지 않는다 — 가맹점 정규화 규칙이 04에 초안만 있다.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private int amount;

    /** 내부 통합 카테고리. 3사 통합 매핑표(06 #11)가 나오면 여기서 변환한다. */
    @Column(nullable = false)
    private String category;

    /** 카드사 원본 카테고리. 매핑표 작성의 근거가 되므로 그대로 보관한다. */
    @Column(name = "source_category")
    private String sourceCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false)
    private TimeSlot timeSlot;

    /** 회고 전에는 묶음이 없다 (04 §1). */
    @Column(name = "behavior_id")
    private Long behaviorId;

    /** userId + occurredAt + merchant + amount 해시 (04 §4). 재업로드 중복을 여기서 막는다. */
    @Column(name = "import_hash", nullable = false)
    private String importHash;

    private Transaction(Long userId, OffsetDateTime occurredAt, String merchant, int amount,
                        String category, String sourceCategory, String importHash) {
        this.userId = userId;
        this.occurredAt = occurredAt;
        this.merchant = merchant;
        this.amount = amount;
        this.category = category;
        this.sourceCategory = sourceCategory;
        this.timeSlot = TimeSlot.from(occurredAt);
        this.importHash = importHash;
    }

    public static Transaction of(Long userId, OffsetDateTime occurredAt, String merchant, int amount,
                                 String category, String sourceCategory, String importHash) {
        return new Transaction(userId, occurredAt, merchant, amount, category, sourceCategory, importHash);
    }

    /** 회고 저장 후 규칙 엔진이 정한 리프 묶음 (E-59). 재계산마다 다시 배정된다. */
    public void assignBehavior(Long behaviorId) {
        this.behaviorId = behaviorId;
    }
}
