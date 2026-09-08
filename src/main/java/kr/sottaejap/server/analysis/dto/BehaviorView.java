package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 묶음 한 건 (05 §2 `GET /behaviors`).
 *
 * <p>{@code parentId}를 그대로 준다 — 회고 저장 응답은 리프 묶음 id를 돌려주는데 그 리프가 롤업됐다면
 * 지도에는 상위 묶음만 있다 (E-72). 화면이 그 사실을 알아야 상위 묶음으로 이동할 수 있다.
 */
public record BehaviorView(
        Long behaviorId,
        String name,
        String clusterKey,
        Long parentId,
        int monthlyTotalAmount,
        Integer avgAmount,
        int txCount,
        int retrospectCount,
        Double burdenRatio,
        Double adjustedSatisfaction,
        EvaluationStatus evaluationStatus,
        Quadrant quadrant,
        Verdict verdict
) {

    public static BehaviorView from(ClusterSnapshot cluster) {
        return new BehaviorView(
                cluster.id(),
                MapPointView.displayName(cluster),
                cluster.clusterKey(),
                cluster.parentId(),
                cluster.monthlyTotalAmount(),
                cluster.avgAmount(),
                cluster.txCount(),
                cluster.retrospectCount(),
                cluster.burdenRatio(),
                cluster.adjustedSatisfaction(),
                cluster.evaluationStatus(),
                cluster.quadrant(),
                cluster.verdict());
    }
}
