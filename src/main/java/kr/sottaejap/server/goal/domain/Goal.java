package kr.sottaejap.server.goal.domain;

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
 * 04 §1 Goal. 스키마 정본은 db/migration이다 (ddl-auto=validate).
 *
 * <p>{@code currentAmount}는 <b>실적</b>이다 — 제안을 채택해도 오르지 않는다 (E-82). 채택은 '예상'이고
 * 예상으로 달성률을 올리면 쓰지도 않은 돈으로 목표가 차오른다. 실적 반영 시점은 월간 리포트 판에서 정한다.
 */
@Entity
@Table(name = "goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private int targetAmount;

    @Column(name = "current_amount", nullable = false)
    private int currentAmount;

    /** soft delete (FR-01-02). 지워도 {@code suggestions.goal_id}는 남긴다 — 채택 이력을 잃지 않는다 (E-83). */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Goal create(Long userId, String name, int targetAmount, Integer currentAmount) {
        Goal goal = new Goal();
        goal.userId = userId;
        goal.name = name;
        goal.targetAmount = targetAmount;
        goal.currentAmount = currentAmount == null ? 0 : currentAmount;
        return goal;
    }

    /**
     * {@code PUT /goals/{id}}는 <b>전체 교체</b>다 — 05 §2의 규칙 표가 {@code POST}와 {@code PUT}에 같이
     * 걸리고 {@code GoalRequest}가 두 필드를 필수로 받는다.
     *
     * <p>{@code currentAmount}만 예외로 "생략하면 그대로 두기"다. 실적이라 화면이 들고 있지 않고,
     * 이름만 고치는 요청이 실적을 0으로 되돌리면 안 된다. {@code POST}의 "생략하면 0"과 다른 이유다.
     */
    public void update(String name, int targetAmount, Integer currentAmount) {
        this.name = name;
        this.targetAmount = targetAmount;
        if (currentAmount != null) {
            this.currentAmount = currentAmount;
        }
    }

    public void delete(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
