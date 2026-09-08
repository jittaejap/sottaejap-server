package kr.sottaejap.server.rules.saving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 절감액 (⑦ · 04 §3). */
class SavingRuleTest {

    @Test
    void 절감액은_평균_단가_곱하기_조정_횟수다() {
        assertEquals(24_000, SavingRule.expectedSaving(12_000, 2));
        assertEquals(0, SavingRule.expectedSaving(0, 3));
    }

    @Test
    void 조정_횟수는_1부터_거래_건수까지다() {
        assertTrue(SavingRule.isValidAdjustCount(1, 8));
        assertTrue(SavingRule.isValidAdjustCount(8, 8));
        assertFalse(SavingRule.isValidAdjustCount(0, 8));
        assertFalse(SavingRule.isValidAdjustCount(9, 8));
    }

    @Test
    void 거래가_없는_묶음은_어떤_횟수도_받지_않는다() {
        assertFalse(SavingRule.isValidAdjustCount(1, 0));
    }
}
