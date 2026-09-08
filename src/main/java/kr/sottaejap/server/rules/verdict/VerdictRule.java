package kr.sottaejap.server.rules.verdict;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.RuleParams;

/**
 * 좌표·처방·상태 3층 판정 (04 §3 · E-11).
 *
 * <p>quadrant(좌표 4)는 가로축 × 세로축, verdict(처방 2)는 <b>세로축 부호만</b>으로 정한다.
 * evaluationStatus가 PENDING이면 둘 다 null이고, DB CHECK 제약이 이를 강제한다.
 */
public final class VerdictRule {

    private VerdictRule() {
    }

    /**
     * @param retrospectCount     UNKNOWN을 포함한 회고 건수 (E-61) — 보류 판정의 분모
     * @param adjustedSatisfaction 축소 추정 보정 만족도
     * @param burdenRatio          월 합계 ÷ 월 예산. 예산이 없거나 0이면 null → quadrant도 null (E-61)
     */
    public static Evaluation evaluate(int retrospectCount,
                                      double adjustedSatisfaction,
                                      Double burdenRatio,
                                      RuleParams params) {
        int pendingMinCount = RuleParams.require(params.pendingMinCount(), "rules.pending-min-count");
        if (retrospectCount < pendingMinCount) {
            return new Evaluation(EvaluationStatus.PENDING, null, null);
        }

        double axisYBoundary = RuleParams.require(params.axisYBoundary(), "rules.axis-y-boundary");
        boolean highSatisfaction = adjustedSatisfaction >= axisYBoundary;
        Verdict verdict = highSatisfaction ? Verdict.SUSTAIN : Verdict.ADJUST;

        Quadrant quadrant = null;
        if (burdenRatio != null) {
            double axisXBoundary = RuleParams.require(params.axisXBoundary(), "rules.axis-x-boundary");
            boolean highBurden = burdenRatio >= axisXBoundary;
            if (highSatisfaction) {
                quadrant = highBurden ? Quadrant.PROTECT : Quadrant.KEEP;
            } else {
                quadrant = highBurden ? Quadrant.PRIORITY : Quadrant.MINOR;
            }
        }

        return new Evaluation(EvaluationStatus.RESOLVED, quadrant, verdict);
    }
}
