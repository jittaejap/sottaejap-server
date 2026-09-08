package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.RuleParamsFixture;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterEngineTest {

    private static final RuleParams PARAMS = RuleParamsFixture.sample();
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Test
    void 같은_키_회고_3건은_리프_하나가_RESOLVED로_나온다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.LOW, 12000),
                tx(2, "쿠팡이츠", Satisfaction.LOW, 15000),
                tx(3, "배달의민족", Satisfaction.HIGH, 9000))), PARAMS);

        assertEquals(1, result.clusters().size());
        ClusterEvaluation leaf = result.clusters().get(0);
        assertEquals("배달|NIGHT|충동|혼자", leaf.clusterKey());
        assertNull(leaf.parentKey());
        assertTrue(leaf.isLeaf());
        assertEquals(3, leaf.retrospectCount());
        assertEquals(-1.0 / 3, leaf.rawAverage(), 1e-9);
        assertEquals(36000, leaf.monthlyTotalAmount());
        assertEquals(3, leaf.txCount());
        assertEquals(12000, leaf.avgAmount());
        assertEquals(0.036, leaf.burdenRatio(), 1e-9);
        assertEquals(EvaluationStatus.RESOLVED, leaf.evaluationStatus());
        assertEquals(Verdict.ADJUST, leaf.verdict());
        assertEquals(List.of(1L, 2L, 3L), leaf.transactionIds());
        assertEquals(List.of("배달의민족", "쿠팡이츠"), leaf.sampleMerchants());
        assertEquals(-1.0 / 3, result.userAverage(), 1e-9);
    }

    @Test
    void 회고_2건은_리프가_PENDING이고_상위_묶음이_먼저_나온다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.LOW, 12000),
                tx(2, "쿠팡이츠", Satisfaction.LOW, 15000))), PARAMS);

        assertEquals(2, result.clusters().size());
        ClusterEvaluation parent = result.clusters().get(0);
        ClusterEvaluation leaf = result.clusters().get(1);
        assertEquals("배달|NIGHT||", parent.clusterKey());
        assertNull(parent.parentKey());
        assertEquals(2, parent.retrospectCount());
        assertEquals(List.of(), parent.transactionIds());
        assertEquals("배달|NIGHT|충동|혼자", leaf.clusterKey());
        assertEquals("배달|NIGHT||", leaf.parentKey());
        assertEquals(EvaluationStatus.PENDING, leaf.evaluationStatus());
        assertNull(leaf.quadrant());
        assertNull(leaf.verdict());
        assertEquals(List.of(1L, 2L), leaf.transactionIds());
    }

    @Test
    void 같은_상위_키의_리프_둘은_상위_묶음에_합쳐져_RESOLVED가_된다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.LOW, 12000, "충동", "혼자"),
                tx(2, "배달의민족", Satisfaction.LOW, 12000, "충동", "혼자"),
                tx(3, "쿠팡이츠", Satisfaction.HIGH, 20000, "식사", "친구"),
                tx(4, "요기요", Satisfaction.HIGH, 20000, "식사", "친구"))), PARAMS);

        assertEquals(3, result.clusters().size());
        ClusterEvaluation parent = result.clusters().get(0);
        assertEquals("배달|NIGHT||", parent.clusterKey());
        assertEquals(4, parent.retrospectCount());
        assertEquals(0.0, parent.rawAverage(), 1e-9);
        assertEquals(EvaluationStatus.RESOLVED, parent.evaluationStatus());
        assertEquals(64000, parent.monthlyTotalAmount());
        assertEquals(List.of("배달의민족", "쿠팡이츠", "요기요"), parent.sampleMerchants());
        assertEquals("배달|NIGHT||", result.clusters().get(1).parentKey());
        assertEquals("배달|NIGHT||", result.clusters().get(2).parentKey());
    }

    @Test
    void 목적_동행인이_없는_회고는_상위_묶음의_직접_구성원이_된다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.LOW, 12000, null, null),
                tx(2, "쿠팡이츠", Satisfaction.LOW, 15000, "충동", "혼자"))), PARAMS);

        assertEquals(2, result.clusters().size());
        ClusterEvaluation parent = result.clusters().get(0);
        assertEquals("배달|NIGHT||", parent.clusterKey());
        assertEquals(2, parent.retrospectCount());
        assertEquals(List.of(1L), parent.transactionIds());
        assertEquals(List.of(2L), result.clusters().get(1).transactionIds());
    }

    @Test
    void 예산이_없으면_burdenRatio와_quadrant는_null이고_verdict는_나온다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(null, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.HIGH, 12000),
                tx(2, "쿠팡이츠", Satisfaction.HIGH, 15000),
                tx(3, "요기요", Satisfaction.HIGH, 9000))), PARAMS);

        ClusterEvaluation leaf = result.clusters().get(0);
        assertNull(leaf.burdenRatio());
        assertNull(leaf.quadrant());
        assertEquals(Verdict.SUSTAIN, leaf.verdict());
    }

    @Test
    void 기준월_밖_거래는_합계에서_빠지고_회고_수에는_들어간다() {
        ClusterRecomputeResult result = ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, List.of(
                tx(1, "배달의민족", Satisfaction.HIGH, 12000, YearMonth.of(2026, 7)),
                tx(2, "쿠팡이츠", Satisfaction.HIGH, 15000, AUGUST),
                tx(3, "요기요", Satisfaction.HIGH, 9000, AUGUST))), PARAMS);

        ClusterEvaluation leaf = result.clusters().get(0);
        assertEquals(3, leaf.retrospectCount());
        assertEquals(24000, leaf.monthlyTotalAmount());
        assertEquals(2, leaf.txCount());
    }

    @Test
    void 입력_순서가_달라도_출력은_같다() {
        List<RetrospectedTransaction> forward = List.of(
                tx(1, "배달의민족", Satisfaction.LOW, 12000),
                tx(2, "쿠팡이츠", Satisfaction.HIGH, 15000, "식사", "친구"),
                tx(3, "요기요", Satisfaction.UNKNOWN, 9000));
        List<RetrospectedTransaction> reversed = List.of(forward.get(2), forward.get(0), forward.get(1));

        assertEquals(ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, forward), PARAMS),
                ClusterEngine.recompute(new ClusterRecomputeInput(1_000_000, AUGUST, reversed), PARAMS));
    }

    static RetrospectedTransaction tx(long id, String merchant, Satisfaction satisfaction, int amount) {
        return tx(id, merchant, satisfaction, amount, "충동", "혼자");
    }

    static RetrospectedTransaction tx(long id, String merchant, Satisfaction satisfaction, int amount, YearMonth month) {
        return new RetrospectedTransaction(id, merchant, "배달", TimeSlot.NIGHT, amount, month, "충동", "혼자", satisfaction);
    }

    static RetrospectedTransaction tx(long id, String merchant, Satisfaction satisfaction, int amount,
                                      String purpose, String companion) {
        return new RetrospectedTransaction(id, merchant, "배달", TimeSlot.NIGHT, amount, AUGUST, purpose, companion, satisfaction);
    }
}
