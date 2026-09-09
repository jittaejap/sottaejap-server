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
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

/**
 * 04 §1 Suggestion — <b>재계산 파생 행</b> (E-81). 사용자가 만드는 것이 아니라 회고 저장 때마다 도는
 * 재계산이 대상 묶음마다 {@code PROPOSED} 한 줄을 두고 제자리 갱신한다. 그래서 생성 API가 없다.
 *
 * <p>{@code userId}가 없다 — 사용자 판별은 {@code behaviorId} → {@code BehaviorCluster.userId} 조인이다.
 *
 * <p>채택하면 그 시점의 {@code avgAmount}로 {@code expectedSaving}이 굳는다. 이후 재계산이 평균 단가를
 * 바꿔도 채택한 금액은 건드리지 않는다 — 사용자가 보고 고른 숫자가 나중에 달라지면 안 된다.
 *
 * <p><b>바뀐 컬럼만 쓴다 ({@code @DynamicUpdate} · E-100).</b> 재계산의 {@link #refresh}는 수치 두 컬럼만, 사용자의
 * {@link #adopt}는 {@code status} · {@code goalId}를 쓴다. 전체 행을 쓰면 재계산이 읽은 뒤 커밋된 채택을 {@code refresh}가
 * 옛 {@code PROPOSED} · {@code goalId = null}로 되써서 사용자의 채택이 조용히 풀린다.
 */
@Entity
@Table(name = "suggestions")
@DynamicUpdate
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

    /**
     * AI가 쓴 이유 문장 (ACTION_PLAN · 05 §3). null이면 화면이 {@code SuggestionReasonTemplate}으로 채운다 (E-38).
     * 내부 AI 응답에는 싣지 않는다 — AI가 자기 출력을 근거로 삼는다 (E-75).
     */
    @Column(columnDefinition = "text")
    private String reason;

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
        applyNumbers(txCount, expectedSaving);
    }

    /** AI가 쓴 이유 문장을 채운다 (ACTION_PLAN). 빈 문장은 템플릿이 낫다 — 호출자가 거른다. */
    public void explain(String reason) {
        this.reason = reason;
    }

    /**
     * 횟수와 절감액을 바꾸면서, <b>값이 실제로 달라졌으면</b> 이유 문장을 버린다. AI 문장은 이 두 숫자를
     * 인용하므로 값이 바뀌면 틀린 말이 된다 — 채택으로 사용자가 횟수를 고쳤을 때도 같다.
     *
     * <p>값이 같아도 무조건 비우면 안 된다. 동기화는 값이 그대로여도 매번 {@link #refresh}를 부르므로,
     * 모든 재계산이 문장을 지워 버린다.
     */
    private void applyNumbers(int adjustCount, int expectedSaving) {
        if (!Integer.valueOf(adjustCount).equals(this.adjustCount)
                || !Integer.valueOf(expectedSaving).equals(this.expectedSaving)) {
            this.reason = null;
        }
        this.adjustCount = adjustCount;
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
        applyNumbers(adjustCount, expectedSaving);
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
