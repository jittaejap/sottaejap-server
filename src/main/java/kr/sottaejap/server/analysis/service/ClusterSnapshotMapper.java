package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/** 엔티티 → 규칙 입력. 규칙 계층이 JPA를 모르게 하는 유일한 경계다. */
public final class ClusterSnapshotMapper {

    private ClusterSnapshotMapper() {
    }

    public static ClusterSnapshot toSnapshot(BehaviorCluster cluster) {
        return new ClusterSnapshot(
                cluster.getId(),
                cluster.getClusterKey(),
                cluster.getDisplayName(),
                cluster.getParentId(),
                cluster.getRetrospectCount(),
                cluster.getAdjustedSatisfaction(),
                cluster.getAvgAmount(),
                cluster.getMonthlyTotalAmount() == null ? 0 : cluster.getMonthlyTotalAmount(),
                cluster.getTxCount() == null ? 0 : cluster.getTxCount(),
                cluster.getBurdenRatio(),
                cluster.getEvaluationStatus(),
                cluster.getQuadrant(),
                cluster.getVerdict());
    }
}
