package kr.sottaejap.server.rules.report;

import kr.sottaejap.server.common.enums.Satisfaction;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 월간 리포트 산식 (E-94 · 04 §3). */
class MonthlyDeltaRuleTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Test
    void 집계는_그_달_거래만_센다() {
        List<MonthlyTransaction> transactions = List.of(
                new MonthlyTransaction(12_000, AUGUST, Satisfaction.LOW, true),
                new MonthlyTransaction(15_000, AUGUST, Satisfaction.LOW, true),
                new MonthlyTransaction(6_000, AUGUST, Satisfaction.HIGH, false),
                new MonthlyTransaction(4_000, AUGUST, null, false),
                // 지난달 거래 — 섞여 들어와도 세지 않는다
                new MonthlyTransaction(30_000, JULY, Satisfaction.LOW, true));

        MonthlyFigures figures = MonthlyDeltaRule.figures(transactions, AUGUST);

        assertEquals(37_000, figures.totalSpending());
        assertEquals(2, figures.unsatisfiedCount());
        assertEquals(2, figures.repeatCount());
    }

    @Test
    void 회고_없는_거래도_지출_합에는_들어간다() {
        MonthlyFigures figures = MonthlyDeltaRule.figures(
                List.of(new MonthlyTransaction(4_000, AUGUST, null, false)), AUGUST);

        assertEquals(4_000, figures.totalSpending());
        assertEquals(0, figures.unsatisfiedCount());
        assertEquals(0, figures.repeatCount());
    }

    @Test
    void 거래가_없는_달은_전부_0이다() {
        assertEquals(new MonthlyFigures(0, 0, 0), MonthlyDeltaRule.figures(List.of(), AUGUST));
    }

    @Test
    void 감소액은_전월_빼기_당월이고_음수를_그대로_낸다() {
        assertEquals(36_000, MonthlyDeltaRule.savedAmount(448_000, 412_000));
        // 더 쓴 달
        assertEquals(-20_000, MonthlyDeltaRule.savedAmount(400_000, 420_000));
    }

    @Test
    void 전월_값이_없으면_감소액은_null이다() {
        assertNull(MonthlyDeltaRule.savedAmount(null, 412_000));
    }

    @Test
    void 배분은_expectedSaving_비율로_내림하고_나머지는_가장_큰_목표에_더한다() {
        // 99,997원을 3 : 1 : 1 로 — 내림하면 59,998 + 19,999 + 19,999 = 99,996, 남는 1원은 가장 큰 목표에
        List<GoalAllocation> allocations = MonthlyDeltaRule.allocate(99_997, List.of(
                new GoalSaving(1L, 30_000),
                new GoalSaving(2L, 10_000),
                new GoalSaving(3L, 10_000)));

        assertEquals(List.of(
                new GoalAllocation(1L, 59_999),
                new GoalAllocation(2L, 19_999),
                new GoalAllocation(3L, 19_999)), allocations);
        assertEquals(99_997, allocations.stream().mapToInt(GoalAllocation::amount).sum());
    }

    @Test
    void 나머지는_배분액이_같으면_id가_작은_목표에_간다() {
        // 5원을 1 : 1 — 2 + 2, 남는 1원은 id 1. 입력 순서가 뒤집혀 있어도 같다
        assertEquals(List.of(new GoalAllocation(1L, 3), new GoalAllocation(2L, 2)),
                MonthlyDeltaRule.allocate(5, List.of(new GoalSaving(2L, 100), new GoalSaving(1L, 100))));
    }

    @Test
    void 목표가_하나면_전액이다() {
        assertEquals(List.of(new GoalAllocation(3L, 36_000)),
                MonthlyDeltaRule.allocate(36_000, List.of(new GoalSaving(3L, 24_000))));
    }

    @Test
    void 감소액이_0_이하이면_배분하지_않는다() {
        List<GoalSaving> savings = List.of(new GoalSaving(1L, 10_000));

        assertTrue(MonthlyDeltaRule.allocate(0, savings).isEmpty());
        assertTrue(MonthlyDeltaRule.allocate(-5_000, savings).isEmpty());
    }

    @Test
    void 붙은_목표가_없거나_가중치_합이_0이면_배분하지_않는다() {
        assertTrue(MonthlyDeltaRule.allocate(36_000, List.of()).isEmpty());
        assertTrue(MonthlyDeltaRule.allocate(36_000, List.of(new GoalSaving(1L, 0))).isEmpty());
    }

    @Test
    void 내림해서_0원이_되는_목표는_목록에_넣지_않는다() {
        // 1원을 1 : 1,000,000 — 둘 다 내림하면 0이고, 남는 1원은 가중치가 큰 뒤 목표에 간다
        assertEquals(List.of(new GoalAllocation(2L, 1)),
                MonthlyDeltaRule.allocate(1, List.of(new GoalSaving(1L, 1), new GoalSaving(2L, 1_000_000))));
    }

    @Test
    void 배분액이_같으면_가중치가_큰_목표가_나머지를_받는다() {
        // 7원을 3 : 4 — 3 + 4 = 7이라 나머지 0. 8원이면 3 + 4 = 7, 남는 1원은 가중치 큰 id 2
        assertEquals(List.of(new GoalAllocation(1L, 3), new GoalAllocation(2L, 5)),
                MonthlyDeltaRule.allocate(8, List.of(new GoalSaving(1L, 3), new GoalSaving(2L, 4))));
    }

    @Test
    void 큰_금액과_큰_가중치에서도_넘치지_않는다() {
        // int 곱이면 넘치는 조합 — long으로 계산해야 한다
        List<GoalAllocation> allocations = MonthlyDeltaRule.allocate(2_000_000_000, List.of(
                new GoalSaving(1L, 2_000_000_000),
                new GoalSaving(2L, 1_000_000_000)));

        assertEquals(2_000_000_000L, allocations.stream().mapToLong(GoalAllocation::amount).sum());
        assertEquals(1_333_333_334, allocations.getFirst().amount());
    }
}
