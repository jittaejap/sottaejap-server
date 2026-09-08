package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kr.sottaejap.server.rules.aggregate.ClusterSnapshotFixture.pending;
import static kr.sottaejap.server.rules.aggregate.ClusterSnapshotFixture.resolved;
import static kr.sottaejap.server.rules.aggregate.ClusterSnapshotFixture.rolledUp;
import static kr.sottaejap.server.rules.aggregate.ClusterSnapshotFixture.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 소비 분석 집계 (⑧ · E-73). */
class AnalysisAggregatorTest {

    private static final int BUDGET = 1_000_000;

    @Test
    void byVerdict는_해당_묶음이_없어도_두_행을_낸다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(), BUDGET);

        assertEquals(List.of(Verdict.SUSTAIN, Verdict.ADJUST),
                summary.byVerdict().stream().map(VerdictSummary::verdict).toList());
        assertTrue(summary.byVerdict().stream().allMatch(row -> row.clusterCount() == 0));
        assertEquals(0.0, summary.byVerdict().getFirst().share());
    }

    @Test
    void share의_분모는_월_예산이다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(
                List.of(resolved(1, "배달|NIGHT||", 360_000, 30, Verdict.ADJUST)), BUDGET);

        assertEquals(0.36, adjust(summary).share());
        assertEquals(360_000, adjust(summary).monthlyTotalAmount());
    }

    @Test
    void 예산이_없으면_share는_null이다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(
                List.of(resolved(1, "배달|NIGHT||", 360_000, 30, Verdict.ADJUST)), null);

        assertNull(adjust(summary).share());
        assertNull(summary.pending().share());
    }

    @Test
    void 보류_묶음은_byVerdict가_아니라_pending으로_센다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "배달|NIGHT||", 100_000, 10, Verdict.ADJUST),
                pending(2, "카페|DAY||", 50_000, 5)), BUDGET);

        assertEquals(1, adjust(summary).clusterCount());
        assertEquals(1, summary.pending().clusterCount());
        assertEquals(50_000, summary.pending().monthlyTotalAmount());
        assertEquals(0.05, summary.pending().share());
    }

    @Test
    void 같은_카테고리_묶음은_한_행으로_합친다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "배달|NIGHT||", 90_000, 6, Verdict.ADJUST),
                resolved(2, "배달|DAY||", 30_000, 4, Verdict.SUSTAIN)), BUDGET);

        assertEquals(1, summary.byCategory().size());
        CategorySummary category = summary.byCategory().getFirst();
        assertEquals("배달", category.category());
        assertEquals(120_000, category.monthlyTotalAmount());
        assertEquals(12_000, category.avgAmount());
    }

    @Test
    void dominantTimeSlot은_합계가_가장_큰_시간대다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "배달|NIGHT||", 90_000, 6, Verdict.ADJUST),
                resolved(2, "배달|DAY||", 30_000, 4, Verdict.SUSTAIN)), BUDGET);

        assertEquals(TimeSlot.NIGHT, summary.byCategory().getFirst().dominantTimeSlot());
    }

    @Test
    void 시간대를_키에_넣지_않는_카테고리는_dominantTimeSlot이_null이다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(
                List.of(resolved(1, "교통|||", 30_000, 10, Verdict.SUSTAIN)), BUDGET);

        assertNull(summary.byCategory().getFirst().dominantTimeSlot());
    }

    @Test
    void 카테고리_판정은_합계가_가장_큰_RESOLVED_묶음의_것이고_보류_금액도_합계에_들어간다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "배달|NIGHT||", 90_000, 6, Verdict.ADJUST),
                resolved(2, "배달|DAY||", 30_000, 4, Verdict.SUSTAIN),
                pending(3, "배달|EVENING||", 200_000, 10)), BUDGET);

        CategorySummary category = summary.byCategory().getFirst();
        assertEquals(Verdict.ADJUST, category.verdict());
        assertEquals(320_000, category.monthlyTotalAmount());
    }

    @Test
    void 전부_보류인_카테고리는_판정이_null이다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(
                List.of(pending(1, "카페|DAY||", 50_000, 5)), BUDGET);

        assertNull(summary.byCategory().getFirst().verdict());
    }

    @Test
    void 거래_건수가_0이면_avgAmount는_null이고_합계_0인_카테고리는_빠진다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                snapshot(1, "배달|NIGHT||", 90_000, 0, EvaluationStatus.RESOLVED, null, Verdict.ADJUST, null),
                snapshot(2, "카페|DAY||", 0, 0, EvaluationStatus.RESOLVED, null, Verdict.SUSTAIN, null)), BUDGET);

        assertEquals(List.of("배달"), summary.byCategory().stream().map(CategorySummary::category).toList());
        assertNull(summary.byCategory().getFirst().avgAmount());
    }

    @Test
    void byCategory는_합계_내림차순이고_동률이면_카테고리_이름순이다() {
        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "카페|DAY||", 50_000, 5, Verdict.SUSTAIN),
                resolved(2, "교통|||", 50_000, 5, Verdict.SUSTAIN),
                resolved(3, "배달|NIGHT||", 90_000, 6, Verdict.ADJUST)), BUDGET);

        assertEquals(List.of("배달", "교통", "카페"),
                summary.byCategory().stream().map(CategorySummary::category).toList());
    }

    @Test
    void 롤업된_리프와_회고_0인_묶음은_집계하지_않는다() {
        ClusterSnapshot noRetrospect = new ClusterSnapshot(9L, "카페|DAY||", null, null, 0, null,
                null, 40_000, 4, null, EvaluationStatus.PENDING, null, null);

        AnalysisSummary summary = AnalysisAggregator.aggregate(List.of(
                resolved(1, "배달|NIGHT||", 90_000, 6, Verdict.ADJUST),
                rolledUp(2, "배달|NIGHT|충동|혼자", 1, 90_000),
                noRetrospect), BUDGET);

        assertEquals(90_000, adjust(summary).monthlyTotalAmount());
        assertEquals(1, adjust(summary).clusterCount());
        assertEquals(List.of("배달"), summary.byCategory().stream().map(CategorySummary::category).toList());
    }

    private static VerdictSummary adjust(AnalysisSummary summary) {
        return summary.byVerdict().stream().filter(row -> row.verdict() == Verdict.ADJUST).findFirst().orElseThrow();
    }
}
