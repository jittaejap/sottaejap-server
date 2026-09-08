package kr.sottaejap.server.rules.verdict;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 좌표·처방·상태 3층 판정 (04 §3 · E-11 · E-61). 파라미터는 E-57 잠정값(보류 3 · Bx 0.1 · By 0). */
class VerdictRuleTest {

    @Test
    void 회고가_보류_임계값보다_적으면_PENDING이고_좌표와_판정은_null이다() {
        Evaluation evaluation = VerdictRule.evaluate(2, 0.5, 0.2, params(3, 0.1, 0.0));

        assertEquals(EvaluationStatus.PENDING, evaluation.evaluationStatus());
        assertNull(evaluation.quadrant());
        assertNull(evaluation.verdict());
    }

    @Test
    void 부담과_만족이_모두_높으면_PROTECT이고_SUSTAIN이다() {
        Evaluation evaluation = VerdictRule.evaluate(3, 0.5, 0.2, params(3, 0.1, 0.0));

        assertEquals(EvaluationStatus.RESOLVED, evaluation.evaluationStatus());
        assertEquals(Quadrant.PROTECT, evaluation.quadrant());
        assertEquals(Verdict.SUSTAIN, evaluation.verdict());
    }

    @Test
    void 부담이_낮고_만족이_높으면_KEEP이고_SUSTAIN이다() {
        Evaluation evaluation = VerdictRule.evaluate(3, 0.5, 0.05, params(3, 0.1, 0.0));

        assertEquals(Quadrant.KEEP, evaluation.quadrant());
        assertEquals(Verdict.SUSTAIN, evaluation.verdict());
    }

    @Test
    void 부담과_만족이_모두_낮으면_MINOR이고_ADJUST다() {
        Evaluation evaluation = VerdictRule.evaluate(3, -0.2, 0.05, params(3, 0.1, 0.0));

        assertEquals(Quadrant.MINOR, evaluation.quadrant());
        assertEquals(Verdict.ADJUST, evaluation.verdict());
    }

    @Test
    void 부담이_높고_만족이_낮으면_PRIORITY이고_ADJUST다() {
        Evaluation evaluation = VerdictRule.evaluate(3, -0.2, 0.2, params(3, 0.1, 0.0));

        assertEquals(Quadrant.PRIORITY, evaluation.quadrant());
        assertEquals(Verdict.ADJUST, evaluation.verdict());
    }

    /** 경계는 포함이다 — 만족도가 By와 같으면 SUSTAIN, 부담이 Bx와 같으면 높은 쪽이다 (04 §3). */
    @Test
    void 경계값은_높은_쪽에_포함된다() {
        Evaluation evaluation = VerdictRule.evaluate(3, 0.0, 0.1, params(3, 0.1, 0.0));

        assertEquals(Quadrant.PROTECT, evaluation.quadrant());
        assertEquals(Verdict.SUSTAIN, evaluation.verdict());
    }

    @Test
    void 부담비율이_없으면_좌표만_null이고_판정은_계산한다() {
        Evaluation evaluation = VerdictRule.evaluate(3, -0.2, null, params(3, 0.1, 0.0));

        assertEquals(EvaluationStatus.RESOLVED, evaluation.evaluationStatus());
        assertNull(evaluation.quadrant());
        assertEquals(Verdict.ADJUST, evaluation.verdict());
    }

    @Test
    void 세로축_경계가_비면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> VerdictRule.evaluate(3, 0.5, 0.2, params(3, 0.1, null)));

        assertTrue(exception.getMessage().contains("rules.axis-y-boundary"));
    }

    @Test
    void 가로축_경계는_부담비율이_있을_때만_필요하다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> VerdictRule.evaluate(3, 0.5, 0.2, params(3, null, 0.0)));
        assertTrue(exception.getMessage().contains("rules.axis-x-boundary"));

        Evaluation evaluation = VerdictRule.evaluate(3, 0.5, null, params(3, null, 0.0));
        assertEquals(Verdict.SUSTAIN, evaluation.verdict());
        assertNull(evaluation.quadrant());
    }

    @Test
    void 보류_임계값이_비면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> VerdictRule.evaluate(3, 0.5, 0.2, params(null, 0.1, 0.0)));

        assertTrue(exception.getMessage().contains("rules.pending-min-count"));
    }

    /** E-57 잠정값 기준. 검사할 값만 바꿔 넣는다. rollup-min-count는 pending 이상이어야 한다 (액션시트 #17). */
    private static RuleParams params(Integer pendingMinCount, Double axisXBoundary, Double axisYBoundary) {
        return new RuleParams(3.0, 3, pendingMinCount, axisXBoundary, axisYBoundary, 3,
                new RuleParams.Sensitivity(3.0, 2.0, 1.5),
                new RuleParams.Candidate(90, 5, 0.1, 2),
                new RuleParams.Cluster(List.of("식사", "식비", "배달", "카페")));
    }
}
