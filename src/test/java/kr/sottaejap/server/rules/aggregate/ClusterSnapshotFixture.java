package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;

/** 집계·정렬 테스트가 쓰는 묶음 스냅샷. 검사에 쓰지 않는 값은 고정한다. */
final class ClusterSnapshotFixture {

    private ClusterSnapshotFixture() {
    }

    static ClusterSnapshot resolved(long id, String clusterKey, int monthlyTotalAmount, int txCount, Verdict verdict) {
        return snapshot(id, clusterKey, monthlyTotalAmount, txCount, EvaluationStatus.RESOLVED,
                verdict == Verdict.SUSTAIN ? Quadrant.PROTECT : Quadrant.PRIORITY, verdict, 0.1);
    }

    static ClusterSnapshot pending(long id, String clusterKey, int monthlyTotalAmount, int txCount) {
        return snapshot(id, clusterKey, monthlyTotalAmount, txCount, EvaluationStatus.PENDING, null, null, null);
    }

    static ClusterSnapshot snapshot(long id, String clusterKey, int monthlyTotalAmount, int txCount,
                                    EvaluationStatus status, Quadrant quadrant, Verdict verdict, Double burdenRatio) {
        return new ClusterSnapshot(id, clusterKey, null, null, 3, -0.4,
                txCount == 0 ? null : monthlyTotalAmount / txCount,
                monthlyTotalAmount, txCount, burdenRatio, status, quadrant, verdict);
    }

    /** 롤업된 리프 — 상위 묶음이 같은 금액을 이미 들고 있다 (E-72). */
    static ClusterSnapshot rolledUp(long id, String clusterKey, long parentId, int monthlyTotalAmount) {
        return new ClusterSnapshot(id, clusterKey, null, parentId, 2, -0.4, monthlyTotalAmount, monthlyTotalAmount, 1,
                0.1, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST);
    }
}
