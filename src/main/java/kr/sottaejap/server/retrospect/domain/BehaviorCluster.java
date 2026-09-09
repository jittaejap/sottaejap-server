package kr.sottaejap.server.retrospect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 04 §1 BehaviorCluster — 규칙 엔진 산출값의 저장소. (user_id, cluster_key) 유일. 값은 {@link #apply}로만 바뀐다.
 *
 * <p>displayName만 AI가 짓는다 (⑤ · E-64). 한 번 정하면 재계산하지 않는다.
 * PENDING이면 quadrant·verdict가 null이어야 한다 — DB CHECK `ck_behavior_clusters_pending`이 강제한다 (E-11).
 *
 * <p><b>바뀐 컬럼만 쓴다 ({@code @DynamicUpdate} · E-100).</b> 재계산의 {@link #apply} · {@link #markEmpty}는 지표만,
 * 명명({@link #rename})은 {@code displayName}만 쓴다. 명명 쪽은 다시 읽어 이름만 바꾸지만 반대 방향은 그럴 수 없다 —
 * 재계산이 읽은 뒤 명명이 커밋되면 전체 행 UPDATE가 {@code display_name = NULL}을 같이 실어 AI가 지은 이름이 지워진다.
 */
@Entity
@Table(name = "behavior_clusters")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviorCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cluster_key", nullable = false)
    private String clusterKey;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "retrospect_count", nullable = false)
    private int retrospectCount;

    @Column(name = "raw_average")
    private Double rawAverage;

    @Column(name = "adjusted_satisfaction")
    private Double adjustedSatisfaction;

    /** 절감액 계산 전용. 지도 가로축은 monthlyTotalAmount다. */
    @Column(name = "avg_amount")
    private Integer avgAmount;

    @Column(name = "monthly_total_amount")
    private Integer monthlyTotalAmount;

    /** `2026-08`. DB는 CHAR(7)이라 JDBC 타입을 맞춰야 validate가 통과한다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "analysis_year_month", length = 7)
    private String analysisYearMonth;

    @Column(name = "tx_count")
    private Integer txCount;

    @Column(name = "burden_ratio")
    private Double burdenRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 10)
    private EvaluationStatus evaluationStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Quadrant quadrant;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Verdict verdict;

    /** 새 키가 처음 나타났을 때. 값은 곧바로 {@link #apply}로 채운다. */
    public static BehaviorCluster create(Long userId, String clusterKey) {
        BehaviorCluster cluster = new BehaviorCluster();
        cluster.userId = userId;
        cluster.clusterKey = clusterKey;
        cluster.evaluationStatus = EvaluationStatus.PENDING;
        return cluster;
    }

    /** 규칙 엔진 산출값을 그대로 옮긴다. parentId는 서비스가 상위 묶음을 먼저 저장한 뒤 넘긴다 (E-59). */
    public void apply(ClusterEvaluation evaluation, Long parentId) {
        this.parentId = parentId;
        this.retrospectCount = evaluation.retrospectCount();
        this.rawAverage = evaluation.rawAverage();
        this.adjustedSatisfaction = evaluation.adjustedSatisfaction();
        this.avgAmount = evaluation.avgAmount();
        this.monthlyTotalAmount = evaluation.monthlyTotalAmount();
        this.analysisYearMonth = evaluation.analysisYearMonth() == null ? null : evaluation.analysisYearMonth().toString();
        this.txCount = evaluation.txCount();
        this.burdenRatio = evaluation.burdenRatio();
        this.evaluationStatus = evaluation.evaluationStatus();
        this.quadrant = evaluation.quadrant();
        this.verdict = evaluation.verdict();
    }

    /**
     * 이번 재계산 결과에 없는 묶음 — 자식이 롤업에서 벗어나 상위 묶음이 비었거나 키가 바뀐 경우. 행은 다른 곳이 참조할 수 있어
     * 지우지 않고 값만 비운다 (E-61). 회고 수 0인 묶음은 지도·메모리에서 뺀다.
     */
    public void markEmpty() {
        this.parentId = null;
        this.retrospectCount = 0;
        this.rawAverage = null;
        this.adjustedSatisfaction = null;
        this.avgAmount = null;
        this.monthlyTotalAmount = 0;
        this.txCount = 0;
        this.burdenRatio = null;
        this.evaluationStatus = EvaluationStatus.PENDING;
        this.quadrant = null;
        this.verdict = null;
    }

    public boolean isEmpty() {
        return retrospectCount == 0;
    }

    /** AI 또는 템플릿 이름. 12자 규칙은 부르는 쪽이 지킨다 (⑤ · FR-05-05). */
    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public boolean hasDisplayName() {
        return displayName != null && !displayName.isBlank();
    }
}
