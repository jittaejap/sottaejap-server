package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;

/** POST /retrospects 응답 (05 §2) — 리프 묶음의 산출값 (E-59). 내부 AI save_reflection도 같은 객체다 (E-66). */
public record RetrospectSaveResponse(
        Long behaviorId,
        String behaviorName,
        int retrospectCount,
        Double adjustedSatisfaction,
        Integer monthlyTotalAmount,
        Integer avgAmount,
        Integer txCount,
        Double burdenRatio,
        EvaluationStatus evaluationStatus,
        Quadrant quadrant,
        Verdict verdict
) {

    public static RetrospectSaveResponse from(BehaviorCluster cluster) {
        return new RetrospectSaveResponse(
                cluster.getId(),
                cluster.getDisplayName(),
                cluster.getRetrospectCount(),
                cluster.getAdjustedSatisfaction(),
                cluster.getMonthlyTotalAmount(),
                cluster.getAvgAmount(),
                cluster.getTxCount(),
                cluster.getBurdenRatio(),
                cluster.getEvaluationStatus(),
                cluster.getQuadrant(),
                cluster.getVerdict());
    }
}
