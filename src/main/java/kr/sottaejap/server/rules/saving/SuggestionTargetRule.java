package kr.sottaejap.server.rules.saving;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 줄여볼 행동을 고른다 (⑦ · E-81). 지도에 보이는 것과 같은 대상을 쓴다 —
 * {@link ClusterSnapshot#isEffective()}(E-72)에 판정 조건만 얹는다.
 *
 * <p><b>좌표를 보지 않는다.</b> {@code PROTECT}가 빠지는 건 좌표 때문이 아니라 판정이 {@code SUSTAIN}이기
 * 때문이고, {@code MINOR}는 부담이 작아도 만족이 낮으므로 줄여볼 대상이다. 예산이 없어 좌표가 아예
 * 없는 묶음도 세로축 부호는 서므로 대상에 들어간다 (E-61).
 */
public final class SuggestionTargetRule {

    private SuggestionTargetRule() {
    }

    public static boolean isTarget(ClusterSnapshot cluster) {
        return cluster.isEffective()
                && cluster.evaluationStatus() == EvaluationStatus.RESOLVED
                && cluster.verdict() == Verdict.ADJUST
                // 평균 단가를 모르면 절감액을 낼 수 없다 — 숫자 없는 제안은 하지 않는다
                && cluster.avgAmount() != null;
    }
}
