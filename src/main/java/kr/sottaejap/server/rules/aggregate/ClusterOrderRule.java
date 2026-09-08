package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.Verdict;

import java.util.Comparator;

/**
 * 지도 · 묶음 목록의 표시 순서 (⑧). 먼저 볼 것이 위로 온다 — 바꿔볼 소비(ADJUST) → 지킬 소비(SUSTAIN)
 * → 아직 판단하기 이른 것(보류), 각 안에서는 부담이 큰 순서다.
 *
 * <p>부담을 모르는 묶음(예산 미설정)은 맨 뒤로 보낸다. 같은 자리는 묶음 키로 고정해 매 호출 같은 순서를 만든다.
 */
public final class ClusterOrderRule {

    private ClusterOrderRule() {
    }

    public static Comparator<ClusterSnapshot> mapOrder() {
        return Comparator.comparingInt(ClusterOrderRule::verdictRank)
                .thenComparing(ClusterSnapshot::burdenRatio, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClusterSnapshot::clusterKey);
    }

    private static int verdictRank(ClusterSnapshot cluster) {
        if (cluster.verdict() == Verdict.ADJUST) {
            return 0;
        }
        return cluster.verdict() == Verdict.SUSTAIN ? 1 : 2;
    }
}
