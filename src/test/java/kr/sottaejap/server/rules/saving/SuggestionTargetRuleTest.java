package kr.sottaejap.server.rules.saving;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 줄여볼 행동 선별 (⑦ · E-81). */
class SuggestionTargetRuleTest {

    @Test
    void 바꿔볼_소비는_대상이다() {
        assertTrue(SuggestionTargetRule.isTarget(cluster(Quadrant.PRIORITY, Verdict.ADJUST)));
    }

    @Test
    void 부담이_작아도_만족이_낮으면_대상이다() {
        assertTrue(SuggestionTargetRule.isTarget(cluster(Quadrant.MINOR, Verdict.ADJUST)));
    }

    @Test
    void 예산이_없어_좌표를_몰라도_판정이_ADJUST면_대상이다() {
        assertTrue(SuggestionTargetRule.isTarget(cluster(null, Verdict.ADJUST)));
    }

    @Test
    void 지킬_소비는_대상이_아니다() {
        assertFalse(SuggestionTargetRule.isTarget(cluster(Quadrant.PROTECT, Verdict.SUSTAIN)));
        assertFalse(SuggestionTargetRule.isTarget(cluster(Quadrant.KEEP, Verdict.SUSTAIN)));
    }

    @Test
    void 보류_묶음은_대상이_아니다() {
        ClusterSnapshot pending = new ClusterSnapshot(1L, "배달|NIGHT||", null, null, 2, -0.4, 12_000,
                96_000, 8, 0.096, EvaluationStatus.PENDING, null, null);

        assertFalse(SuggestionTargetRule.isTarget(pending));
    }

    @Test
    void 롤업된_리프와_회고_0인_묶음은_대상이_아니다() {
        ClusterSnapshot rolledUp = new ClusterSnapshot(1L, "배달|NIGHT|충동|혼자", null, 9L, 2, -0.4, 12_000,
                96_000, 8, 0.096, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST);
        ClusterSnapshot empty = new ClusterSnapshot(2L, "배달|NIGHT||", null, null, 0, null, 12_000,
                96_000, 8, 0.096, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST);

        assertFalse(SuggestionTargetRule.isTarget(rolledUp));
        assertFalse(SuggestionTargetRule.isTarget(empty));
    }

    @Test
    void 평균_단가를_모르면_대상이_아니다() {
        ClusterSnapshot noAvg = new ClusterSnapshot(1L, "배달|NIGHT||", null, null, 4, -0.4, null,
                0, 0, null, EvaluationStatus.RESOLVED, null, Verdict.ADJUST);

        assertFalse(SuggestionTargetRule.isTarget(noAvg));
    }

    private static ClusterSnapshot cluster(Quadrant quadrant, Verdict verdict) {
        return new ClusterSnapshot(1L, "배달|NIGHT||", "심야 배달", null, 4, -0.42, 12_000, 96_000, 8,
                quadrant == null ? null : 0.096, EvaluationStatus.RESOLVED, quadrant, verdict);
    }
}
