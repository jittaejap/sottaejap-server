package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 롤업 판정 고정 케이스 (③ · E-59). 기준 건수는 잠정 3이다 (E-57). */
class RollupRuleTest {

    private static final RuleParams PARAMS = params(3);

    @Test
    void 회고가_기준_건수보다_적으면_상위_묶음에_붙인다() {
        assertTrue(RollupRule.needsRollup(2, PARAMS));
    }

    @Test
    void 회고가_없으면_상위_묶음에_붙인다() {
        assertTrue(RollupRule.needsRollup(0, PARAMS));
    }

    @Test
    void 회고가_기준_건수와_같으면_리프로_남는다() {
        assertFalse(RollupRule.needsRollup(3, PARAMS));
        assertFalse(RollupRule.needsRollup(4, PARAMS));
    }

    @Test
    void 롤업_기준_건수가_비면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> RollupRule.needsRollup(2, params(null)));
        assertTrue(exception.getMessage().contains("rules.rollup-min-count"));
    }

    private static RuleParams params(Integer rollupMinCount) {
        return new RuleParams(3.0, rollupMinCount, null, 0.1, 0.0, 3,
                new RuleParams.Sensitivity(3.0, 2.0, 1.5),
                new RuleParams.Candidate(90, 5, 0.1, 2),
                new RuleParams.Cluster(List.of("식사")));
    }
}
