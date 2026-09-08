package kr.sottaejap.server.rules.saving;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 제안 목록 순서 (⑦ · E-81) — 좌표 기준이다. */
class SuggestionOrderRuleTest {

    @Test
    void 부담이_큰_쪽이_먼저다() {
        List<ClusterSnapshot> sorted = sort(
                cluster(1L, "카페|DAY||", Quadrant.MINOR, 0.02),
                cluster(2L, "배달|NIGHT||", Quadrant.PRIORITY, 0.3));

        assertEquals(List.of(2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    @Test
    void 같은_좌표면_부담_내림차순이다() {
        List<ClusterSnapshot> sorted = sort(
                cluster(1L, "카페|DAY||", Quadrant.PRIORITY, 0.2),
                cluster(2L, "배달|NIGHT||", Quadrant.PRIORITY, 0.3));

        assertEquals(List.of(2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    @Test
    void 부담을_모르는_묶음은_맨_뒤다() {
        List<ClusterSnapshot> sorted = sort(
                cluster(1L, "카페|DAY||", null, null),
                cluster(2L, "배달|NIGHT||", Quadrant.MINOR, 0.02));

        assertEquals(List.of(2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    private static List<ClusterSnapshot> sort(ClusterSnapshot... clusters) {
        return List.of(clusters).stream().sorted(SuggestionOrderRule.comparator()).toList();
    }

    private static ClusterSnapshot cluster(long id, String key, Quadrant quadrant, Double burdenRatio) {
        return new ClusterSnapshot(id, key, null, null, 4, -0.42, 12_000, 96_000, 8, burdenRatio,
                EvaluationStatus.RESOLVED, quadrant, Verdict.ADJUST);
    }
}
