package kr.sottaejap.server.rules.verdict;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;

/**
 * 판정 결과 (E-11). PENDING이면 quadrant·verdict 모두 null. RESOLVED인데 burdenRatio가 없으면 quadrant만 null (E-61).
 */
public record Evaluation(EvaluationStatus evaluationStatus, Quadrant quadrant, Verdict verdict) {
}
