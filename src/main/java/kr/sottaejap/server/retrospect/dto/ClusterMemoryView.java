package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;

/** 내부 AI `get_memory`의 clusters 항목 (05 §3). */
public record ClusterMemoryView(
        Long behaviorId,
        String name,
        String clusterKey,
        int retrospectCount,
        Double adjustedSatisfaction,
        Verdict verdict
) {

    public static ClusterMemoryView from(BehaviorCluster cluster) {
        return new ClusterMemoryView(
                cluster.getId(),
                cluster.getDisplayName(),
                cluster.getClusterKey(),
                cluster.getRetrospectCount(),
                cluster.getAdjustedSatisfaction(),
                cluster.getVerdict());
    }
}
