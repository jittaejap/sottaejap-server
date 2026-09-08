package kr.sottaejap.server.rules.saving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 목표 달성률 (⑦ · E-83). */
class GoalProjectionRuleTest {

    @Test
    void 달성률은_실적_나누기_목표다() {
        assertEquals(0.25, GoalProjectionRule.achievementRate(250_000, 1_000_000));
    }

    @Test
    void 전망은_채택한_절감액까지_더한다() {
        assertEquals(0.274, GoalProjectionRule.projectedRate(250_000, 24_000, 1_000_000), 1e-9);
    }

    @Test
    void 목표_금액이_0_이하면_비율이_없다() {
        assertNull(GoalProjectionRule.achievementRate(250_000, 0));
        assertNull(GoalProjectionRule.projectedRate(250_000, 24_000, -1));
    }

    @Test
    void 반올림하지_않는다() {
        // 화면이 반올림한다 — 서버가 반올림하면 두 비율의 차이가 사라진다
        assertEquals(1.0 / 3, GoalProjectionRule.achievementRate(1, 3), 1e-12);
    }
}
