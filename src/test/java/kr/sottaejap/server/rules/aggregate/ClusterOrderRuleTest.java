package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kr.sottaejap.server.rules.aggregate.ClusterSnapshotFixture.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 지도 · 목록 표시 순서 — 먼저 볼 것이 위로. */
class ClusterOrderRuleTest {

    @Test
    void 바꿔볼_소비가_지킬_소비보다_앞이다() {
        List<ClusterSnapshot> sorted = sort(
                snapshot(1, "카페|DAY||", 50_000, 5, EvaluationStatus.RESOLVED, Quadrant.PROTECT, Verdict.SUSTAIN, 0.9),
                snapshot(2, "배달|NIGHT||", 10_000, 1, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST, 0.1));

        assertEquals(List.of(2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    @Test
    void 보류_묶음은_맨_뒤다() {
        List<ClusterSnapshot> sorted = sort(
                snapshot(1, "카페|DAY||", 50_000, 5, EvaluationStatus.PENDING, null, null, 0.9),
                snapshot(2, "배달|NIGHT||", 10_000, 1, EvaluationStatus.RESOLVED, Quadrant.KEEP, Verdict.SUSTAIN, 0.1));

        assertEquals(List.of(2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    @Test
    void 같은_판정이면_부담_내림차순이고_부담을_모르면_맨_뒤다() {
        List<ClusterSnapshot> sorted = sort(
                snapshot(1, "카페|DAY||", 10_000, 1, EvaluationStatus.RESOLVED, null, Verdict.ADJUST, null),
                snapshot(2, "배달|NIGHT||", 20_000, 2, EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST, 0.02),
                snapshot(3, "편의점|||", 30_000, 3, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST, 0.3));

        assertEquals(List.of(3L, 2L, 1L), sorted.stream().map(ClusterSnapshot::id).toList());
    }

    private static List<ClusterSnapshot> sort(ClusterSnapshot... clusters) {
        return List.of(clusters).stream().sorted(ClusterOrderRule.mapOrder()).toList();
    }
}
