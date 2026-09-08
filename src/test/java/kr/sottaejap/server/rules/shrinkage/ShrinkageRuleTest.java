package kr.sottaejap.server.rules.shrinkage;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 축소 추정 보정 (04 §3 · E-61). 잠정값은 E-57과 같게 둔다. */
class ShrinkageRuleTest {

    private static final double DELTA = 1e-9;

    @Test
    void 원평균은_UNKNOWN을_분모에서_제외한다() {
        List<Satisfaction> satisfactions = List.of(Satisfaction.HIGH, Satisfaction.LOW, Satisfaction.UNKNOWN);

        assertEquals(0.0, ShrinkageRule.rawAverage(satisfactions), DELTA);
        assertEquals(2, ShrinkageRule.scoredCount(satisfactions));
    }

    @Test
    void 원평균은_HIGH를_더하고_LOW를_뺀다() {
        List<Satisfaction> satisfactions = List.of(Satisfaction.HIGH, Satisfaction.HIGH, Satisfaction.LOW);

        assertEquals(1.0 / 3, ShrinkageRule.rawAverage(satisfactions), DELTA);
        assertEquals(3, ShrinkageRule.scoredCount(satisfactions));
    }

    @Test
    void 원평균은_전부_UNKNOWN이면_null이다() {
        assertNull(ShrinkageRule.rawAverage(List.of(Satisfaction.UNKNOWN)));
        assertEquals(0, ShrinkageRule.scoredCount(List.of(Satisfaction.UNKNOWN)));
    }

    @Test
    void 원평균은_회고가_없으면_null이다() {
        assertNull(ShrinkageRule.rawAverage(List.of()));
    }

    @Test
    void 사용자_평균은_표본이_없으면_척도_중립값_0이다() {
        assertEquals(0.0, ShrinkageRule.userAverage(List.of()), DELTA);
        assertEquals(0.0, ShrinkageRule.userAverage(List.of(Satisfaction.UNKNOWN)), DELTA);
    }

    @Test
    void 사용자_평균도_같은_산식을_쓴다() {
        assertEquals(1.0 / 3,
                ShrinkageRule.userAverage(List.of(Satisfaction.HIGH, Satisfaction.HIGH, Satisfaction.LOW)), DELTA);
    }

    @Test
    void 보정_만족도는_사용자_평균_쪽으로_끌어당긴다() {
        double adjusted = ShrinkageRule.adjust(4, -0.5, 0.1, params(3.0, 0.0));

        assertEquals((4 * -0.5 + 3 * 0.1) / 7, adjusted, DELTA);
    }

    @Test
    void 보정_만족도는_원평균이_null이면_사용자_평균_그대로다() {
        assertEquals(0.1, ShrinkageRule.adjust(0, null, 0.1, params(3.0, 0.0)), DELTA);
    }

    @Test
    void 보정_만족도는_k가_비면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> ShrinkageRule.adjust(4, -0.5, 0.1, params(null, 0.0)));

        assertTrue(exception.getMessage().contains("rules.shrinkage-k"));
    }

    /** E-57 잠정값 기준 파라미터. 검사할 값만 바꿔 넣는다. */
    static RuleParams params(Double shrinkageK, Double axisYBoundary) {
        return new RuleParams(shrinkageK, 3, 3, 0.1, axisYBoundary, 3,
                new RuleParams.Sensitivity(3.0, 2.0, 1.5),
                new RuleParams.Candidate(90, 5, 0.1, 2),
                new RuleParams.Cluster(List.of("식사", "식비", "배달", "카페")));
    }
}
