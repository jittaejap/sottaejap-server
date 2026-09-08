package kr.sottaejap.server.rules.saving;

import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

import java.util.Comparator;

/**
 * 제안 목록의 순서 (⑦ · E-81). <b>좌표 기준</b>이다 — 부담이 크고 만족이 낮은 것부터 줄여야 효과가 크다.
 *
 * <p>{@code rules/aggregate/ClusterOrderRule}과 다르다. 저건 지도·묶음 목록의 <b>판정 기준</b>
 * (ADJUST → SUSTAIN → 보류)이고, 제안 목록에는 ADJUST만 들어오므로 그 정렬로는 줄이 서지 않는다.
 *
 * <p>부담을 모르는 묶음(예산 미설정)은 맨 뒤로 보낸다. 좌표·부담이 같으면 여기서 순서를 정하지 않는다 —
 * 04 §3의 마지막 동점 기준은 <b>제안 id 오름차순</b>이고, id는 엔티티의 것이라 규칙 계층이 알지 못한다.
 * 여기서 {@code clusterKey}로 끊으면 부르는 쪽의 id 비교가 영영 실행되지 않는다.
 */
public final class SuggestionOrderRule {

    private SuggestionOrderRule() {
    }

    public static Comparator<ClusterSnapshot> comparator() {
        return Comparator.comparingInt(SuggestionOrderRule::quadrantRank)
                .thenComparing(ClusterSnapshot::burdenRatio, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static int quadrantRank(ClusterSnapshot cluster) {
        if (cluster.quadrant() == Quadrant.PRIORITY) {
            return 0;
        }
        return cluster.quadrant() == Quadrant.MINOR ? 1 : 2;
    }
}
