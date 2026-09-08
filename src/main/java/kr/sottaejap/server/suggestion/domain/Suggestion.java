package kr.sottaejap.server.suggestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 04 §1 Suggestion — <b>재계산 파생 행</b> (E-81). 사용자가 만드는 것이 아니라 회고 저장 때마다 도는
 * 재계산이 대상 묶음마다 {@code PROPOSED} 한 줄을 두고 제자리 갱신한다. 그래서 생성 API가 없다.
 *
 * <p>{@code userId}가 없다 — 사용자 판별은 {@code behaviorId} → {@code BehaviorCluster.userId} 조인이다.
 *
 * <p>채택하면 그 시점의 {@code avgAmount}로 {@code expectedSaving}이 굳는다. 이후 재계산이 평균 단가를
 * 바꿔도 채택한 금액은 건드리지 않는다 — 사용자가 보고 고른 숫자가 나중에 달라지면 안 된다.
 */
@Entity
@Table(name = "suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "behavior_id", nullable = false)
    private Long behaviorId;

    /** 제안 시점에는 묶음의 {@code txCount}, 채택할 때 사용자가 고른다 (FR-08-02). */
    @Column(name = "adjust_count")
    private Integer adjustCount;

    @Column(name = "expected_saving")
    private Integer expectedSaving;

    @Column(name = "goal_id")
    private Long goalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SuggestionStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 재계산이 새 대상을 발견했을 때. 횟수 기본값은 묶음의 거래 건수다 — "전부 줄이면"이 출발점이다. */
    public static Suggestion propose(Long behaviorId, int txCount, int expectedSaving) {
        Suggestion suggestion = new Suggestion();
        suggestion.behaviorId = behaviorId;
        suggestion.adjustCount = txCount;
        suggestion.expectedSaving = expectedSaving;
        suggestion.status = SuggestionStatus.PROPOSED;
        return suggestion;
    }

    /**
     * 재계산이 이미 있는 {@code PROPOSED}를 다시 계산했을 때. 지우고 새로 넣지 않는 이유는 부분 유일
     * 인덱스 때문이다 — Hibernate가 INSERT를 DELETE보다 먼저 내보내 충돌한다.
     */
    public void refresh(int txCount, int expectedSaving) {
        this.adjustCount = txCount;
        this.expectedSaving = expectedSaving;
    }

    /**
     * 채택 (FR-08-02·03·04). 이미 ADOPTED여도 횟수·목표를 고칠 수 있다 (FR-08-05 · E-82).
     *
     * <p>{@code goalId}가 null이면 <b>기존 연결을 그대로 둔다.</b> S5는 스텝퍼와 목표 배분이 별개 요소라
     * 횟수만 고치는 요청이 목표를 지우면 안 된다 — 사용자가 건드리지 않은 값이다. {@code Goal.update}와
     * {@code PUT /users/me/settings}가 쓰는 "null은 그대로 두기"와 같은 규칙이다.
     */
    public void adopt(int adjustCount, int expectedSaving, Long goalId) {
        this.adjustCount = adjustCount;
        this.expectedSaving = expectedSaving;
        if (goalId != null) {
            this.goalId = goalId;
        }
        this.status = SuggestionStatus.ADOPTED;
    }

    /** 거절 · 철회. 종단 상태라 여기서 다시 채택할 수 없다 (E-82). */
    public void reject() {
        this.status = SuggestionStatus.REJECTED;
    }

    public boolean isProposed() {
        return status == SuggestionStatus.PROPOSED;
    }

    /** 재계산이 손대도 되는 행인지. 사용자가 정한 상태는 보존한다 (E-81). */
    public boolean isDerived() {
        return isProposed();
    }
}
