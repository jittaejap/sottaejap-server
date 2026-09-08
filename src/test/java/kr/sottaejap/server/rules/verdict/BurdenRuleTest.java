package kr.sottaejap.server.rules.verdict;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.rules.cluster.RetrospectedTransaction;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 분석 기준월 집계 (04 §3 · B-11 · E-60 · E-61). */
class BurdenRuleTest {

    private static final double DELTA = 1e-9;
    private static final YearMonth ANALYSIS_MONTH = YearMonth.of(2025, 8);
    private static final YearMonth EMPTY_MONTH = YearMonth.of(2025, 6);

    @Test
    void 기준월_거래만_더하고_평균은_정수_나눗셈이다() {
        MonthlyBurden burden = BurdenRule.of(transactions(), ANALYSIS_MONTH, 1_000_000);

        assertEquals(27000, burden.monthlyTotalAmount());
        assertEquals(2, burden.txCount());
        assertEquals(13500, burden.avgAmount());
    }

    @Test
    void 부담비율은_월_합계를_월_예산으로_나눈다() {
        MonthlyBurden burden = BurdenRule.of(transactions(), ANALYSIS_MONTH, 1_000_000);

        assertEquals(0.027, burden.burdenRatio(), DELTA);
    }

    @Test
    void 부담비율은_예산이_없으면_null이다() {
        assertNull(BurdenRule.of(transactions(), ANALYSIS_MONTH, null).burdenRatio());
    }

    @Test
    void 부담비율은_예산이_0이면_null이다() {
        assertNull(BurdenRule.of(transactions(), ANALYSIS_MONTH, 0).burdenRatio());
    }

    @Test
    void 기준월_거래가_없으면_합계_0_건수_0_평균_null이다() {
        MonthlyBurden burden = BurdenRule.of(transactions(), EMPTY_MONTH, 1_000_000);

        assertEquals(0, burden.monthlyTotalAmount());
        assertEquals(0, burden.txCount());
        assertNull(burden.avgAmount());
    }

    /** 합계 0과 예산 없음을 구분한다 — 예산이 있으면 비율은 null이 아니라 0.0이다 (E-61). */
    @Test
    void 합계가_0이어도_예산이_있으면_부담비율은_0이다() {
        MonthlyBurden withBudget = BurdenRule.of(transactions(), EMPTY_MONTH, 1_000_000);
        MonthlyBurden withoutBudget = BurdenRule.of(transactions(), EMPTY_MONTH, null);

        assertEquals(0.0, withBudget.burdenRatio(), DELTA);
        assertNull(withoutBudget.burdenRatio());
    }

    @Test
    void 거래_목록이_비어도_계산한다() {
        MonthlyBurden burden = BurdenRule.of(List.of(), ANALYSIS_MONTH, 1_000_000);

        assertEquals(0, burden.monthlyTotalAmount());
        assertEquals(0, burden.txCount());
        assertNull(burden.avgAmount());
        assertEquals(0.0, burden.burdenRatio(), DELTA);
    }

    /** 기준월 2건(12000 · 15000)과 다른 달 1건. */
    private static List<RetrospectedTransaction> transactions() {
        return List.of(
                transaction(1L, 12000, ANALYSIS_MONTH),
                transaction(2L, 15000, ANALYSIS_MONTH),
                transaction(3L, 99000, YearMonth.of(2025, 7)));
    }

    private static RetrospectedTransaction transaction(long id, int amount, YearMonth occurredMonth) {
        return new RetrospectedTransaction(id, "가맹점" + id, "기타", TimeSlot.EVENING, amount, occurredMonth,
                null, null, Satisfaction.HIGH);
    }
}
