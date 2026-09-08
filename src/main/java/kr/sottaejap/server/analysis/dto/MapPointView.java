package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.analysis.service.PrescriptionTemplate;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.service.ClusterNameTemplate;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 만족도 지도의 점 하나 (05 §2 `GET /satisfaction-map`).
 *
 * <p>{@code name}은 AI가 지은 이름이고, 아직 붙지 않았으면 묶음 키에서 만든 이름으로 대체한다 (E-64) —
 * 재계산이 이름 짓기보다 먼저 끝나므로 이름 없는 묶음이 잠깐 존재한다.
 */
public record MapPointView(
        Long behaviorId,
        String name,
        int monthlyTotalAmount,
        Integer avgAmount,
        int txCount,
        Double burdenRatio,
        Double adjustedSatisfaction,
        int retrospectCount,
        EvaluationStatus evaluationStatus,
        Quadrant quadrant,
        Verdict verdict,
        String prescription,
        CtaView cta
) {

    public static MapPointView from(ClusterSnapshot cluster) {
        return new MapPointView(
                cluster.id(),
                displayName(cluster),
                cluster.monthlyTotalAmount(),
                cluster.avgAmount(),
                cluster.txCount(),
                cluster.burdenRatio(),
                cluster.adjustedSatisfaction(),
                cluster.retrospectCount(),
                cluster.evaluationStatus(),
                cluster.quadrant(),
                cluster.verdict(),
                PrescriptionTemplate.prescriptionFor(cluster),
                PrescriptionTemplate.ctaFor(cluster));
    }

    static String displayName(ClusterSnapshot cluster) {
        String name = cluster.displayName();
        return name == null || name.isBlank() ? ClusterNameTemplate.nameFor(cluster.clusterKey()) : name;
    }
}
