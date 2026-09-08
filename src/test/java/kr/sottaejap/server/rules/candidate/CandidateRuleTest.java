package kr.sottaejap.server.rules.candidate;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 후보 선별 ⓪ 규칙 (E-62 · B-8). 파라미터 값은 `RuleEngineDeterminismTest.sampleParams()`와 같다
 * (candidate: baseline 90 · min-samples 5 · budget-ratio 0.1 · repeated-low 2, sensitivity standard 2.0).
 */
class CandidateRuleTest {

    private static final double STANDARD_MULTIPLIER = 2.0;
    private static final List<Integer> NO_BASELINE = List.of();

    private static RuleParams params() {
        return params(new RuleParams.Candidate(90, 5, 0.1, 2));
    }

    private static RuleParams params(RuleParams.Candidate candidate) {
        return new RuleParams(3.0, 3, 3, 0.1, 0.0, 3,
                new RuleParams.Sensitivity(3.0, STANDARD_MULTIPLIER, 1.5),
                candidate,
                new RuleParams.Cluster(List.of("식사", "식비", "배달", "카페")));
    }

    private static CandidateInput input(int amount, List<Integer> sameSlot, List<Integer> sameCategory,
                                        Double multiplier, Integer monthlyBudget, int lowCount) {
        return new CandidateInput(amount, sameSlot, sameCategory, multiplier, monthlyBudget, lowCount);
    }

    @Test
    void 예산_비율을_넘으면_THRESHOLD_EXCEEDED다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 0);
        assertEquals(Optional.of(ReasonCode.THRESHOLD_EXCEEDED), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 예산_비율과_같아도_THRESHOLD_EXCEEDED다() {
        CandidateInput input = input(100_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 0);
        assertEquals(Optional.of(ReasonCode.THRESHOLD_EXCEEDED), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 예산_비율보다_적으면_THRESHOLD가_아니다() {
        CandidateInput input = input(99_999, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 예산이_없으면_금액이_커도_THRESHOLD가_아니다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 예산이_0이면_THRESHOLD를_적용하지_않는다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 0, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 시간대_표본이_충분하면_중앙값_배수_초과는_TIMESLOT_OUTLIER다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000, 12_000);
        CandidateInput input = input(25_000, sameSlot, NO_BASELINE, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.of(ReasonCode.TIMESLOT_OUTLIER), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 중앙값_배수와_같으면_이상치가_아니다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000, 12_000);
        CandidateInput input = input(20_000, sameSlot, NO_BASELINE, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 시간대_표본이_부족하면_카테고리_기준선으로_내려간다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000);
        List<Integer> sameCategory = List.of(3_000, 4_000, 5_000, 5_000, 6_000, 7_000);
        CandidateInput input = input(15_000, sameSlot, sameCategory, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.of(ReasonCode.TIMESLOT_OUTLIER), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 두_기준선_모두_표본이_부족하면_이상치를_적용하지_않는다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000);
        List<Integer> sameCategory = List.of(3_000, 4_000, 5_000, 6_000);
        CandidateInput input = input(15_000, sameSlot, sameCategory, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 배수가_비어_오면_이상치를_적용하지_않는다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000, 12_000);
        CandidateInput input = input(25_000, sameSlot, NO_BASELINE, null, null, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void LOW_회고가_기준_건수_이상이면_REPEATED_LOW_SATISFACTION이다() {
        CandidateInput input = input(5_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, null, 2);
        assertEquals(Optional.of(ReasonCode.REPEATED_LOW_SATISFACTION), CandidateRule.evaluate(input, params()));
    }

    @Test
    void LOW_회고가_기준_건수보다_적으면_해당하지_않는다() {
        CandidateInput input = input(5_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, null, 1);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 어느_규칙에도_맞지_않으면_비어_있다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, null, 0);
        assertEquals(Optional.empty(), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 예산_초과가_이상치와_LOW보다_우선한다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000, 12_000);
        CandidateInput input = input(120_000, sameSlot, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 3);
        assertEquals(Optional.of(ReasonCode.THRESHOLD_EXCEEDED), CandidateRule.evaluate(input, params()));
    }

    @Test
    void 이상치가_LOW보다_우선한다() {
        List<Integer> sameSlot = List.of(8_000, 9_000, 10_000, 11_000, 12_000);
        CandidateInput input = input(25_000, sameSlot, NO_BASELINE, STANDARD_MULTIPLIER, null, 3);
        assertEquals(Optional.of(ReasonCode.TIMESLOT_OUTLIER), CandidateRule.evaluate(input, params()));
    }

    @Test
    void candidate_파라미터가_비면_계산을_거부한다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 0);
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> CandidateRule.evaluate(input, params(null)));
        assertEquals(true, exception.getMessage().contains("rules.candidate"));
    }

    @Test
    void 예산이_있는데_비율이_비면_계산을_거부한다() {
        CandidateInput input = input(120_000, NO_BASELINE, NO_BASELINE, STANDARD_MULTIPLIER, 1_000_000, 0);
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> CandidateRule.evaluate(input, params(new RuleParams.Candidate(90, 5, null, 2))));
        assertEquals(true, exception.getMessage().contains("rules.candidate.big-amount-budget-ratio"));
    }

    @Test
    void 같은_입력이면_같은_결과다() {
        List<Integer> sameSlot = List.of(12_000, 8_000, 11_000, 9_000, 10_000);
        CandidateInput input = input(25_000, sameSlot, NO_BASELINE, STANDARD_MULTIPLIER, null, 0);
        assertEquals(CandidateRule.evaluate(input, params()), CandidateRule.evaluate(input, params()));
    }
}
