package kr.sottaejap.server.rules.candidate;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.rules.RuleParams;

import java.util.List;
import java.util.Optional;

/**
 * 회고 후보 선별 ⓪ — 거래 한 건의 선정 사유를 정한다 (E-62 · B-8).
 *
 * <p>우선순위는 ③ {@code THRESHOLD_EXCEEDED} &gt; ④ {@code TIMESLOT_OUTLIER} &gt; ⑤ {@code REPEATED_LOW_SATISFACTION}이다.
 * 어느 규칙에도 맞지 않으면 {@link Optional#empty()}를 돌려주고, 서비스가 직접 선택(`MANUAL_PICK`)으로 쓴다 (E-63).
 * {@code ONBOARDING_SAMPLE}과 {@code MANUAL_PICK}은 이 규칙이 내지 않는다.
 *
 * <p>E-62 ①(D+1)과 ②(이미 회고된 거래 제외)는 날짜를 봐야 하므로 서비스가 쿼리로 거른다 — 규칙 엔진은 결정론이다 (E-18).
 */
public final class CandidateRule {

    private CandidateRule() {
    }

    /** 규칙을 실제로 평가하는 지점에서만 파라미터를 받는다. null이면 기본값으로 대체하지 않고 계산을 거부한다 (E-57). */
    public static Optional<ReasonCode> evaluate(CandidateInput input, RuleParams params) {
        RuleParams.Candidate candidate = RuleParams.require(params.candidate(), "rules.candidate");

        if (exceedsBudgetThreshold(input, candidate)) {
            return Optional.of(ReasonCode.THRESHOLD_EXCEEDED);
        }
        if (isTimeSlotOutlier(input, candidate)) {
            return Optional.of(ReasonCode.TIMESLOT_OUTLIER);
        }
        if (hasRepeatedLowSatisfaction(input, candidate)) {
            return Optional.of(ReasonCode.REPEATED_LOW_SATISFACTION);
        }
        return Optional.empty();
    }

    /**
     * ③ 사용자가 정한 기준 금액이 있으면 `amount ≥ outlierBaseAmount`, 없으면 `amount ≥ monthlyBudget × big-amount-budget-ratio`다 (E-115).
     * 둘 다 없으면 미적용이다.
     *
     * <p>사용자가 직접 적은 금액이 잠정 파라미터(E-57 `0.1`)보다 세다. <b>예산이 없어도 기준 금액이 있으면 판정이 열린다.</b>
     * 0 이하는 폴백으로 되돌린다 — 모든 거래가 후보가 되어 목록이 무의미해진다. DTO가 `@Positive`로 막지만
     * 규칙 엔진은 입력값만 보고 스스로 지킨다 (E-18).
     */
    private static boolean exceedsBudgetThreshold(CandidateInput input, RuleParams.Candidate candidate) {
        Integer baseAmount = input.outlierBaseAmount();
        if (baseAmount != null && baseAmount > 0) {
            return input.amount() >= baseAmount;
        }
        Integer monthlyBudget = input.monthlyBudget();
        if (monthlyBudget == null || monthlyBudget == 0) {
            return false;
        }
        double ratio = RuleParams.require(candidate.bigAmountBudgetRatio(), "rules.candidate.big-amount-budget-ratio");
        return input.amount() >= monthlyBudget * ratio;
    }

    /**
     * ④ 기준선 중앙값의 배수를 넘으면 이상치다. 같은 금액이면 이상치가 아니다(초과 비교).
     *
     * <p>기준선은 시간대 표본이 `outlier-min-samples` 이상이면 그것을, 아니면 카테고리 전체 표본을 쓴다.
     * 둘 다 부족하면 미적용이다. 배수는 서비스가 `User.outlierThreshold` 또는 `sensitivity.standard`를 채워 보내는 것이
     * 계약이지만, 비어 오면 판정을 만들지 않는다.
     */
    private static boolean isTimeSlotOutlier(CandidateInput input, RuleParams.Candidate candidate) {
        Double multiplier = input.outlierMultiplier();
        if (multiplier == null) {
            return false;
        }
        int minSamples = RuleParams.require(candidate.outlierMinSamples(), "rules.candidate.outlier-min-samples");
        List<Integer> baseline = selectBaseline(input, minSamples);
        if (baseline == null) {
            return false;
        }
        return input.amount() > Median.of(baseline) * multiplier;
    }

    /** 시간대 → 카테고리 순으로 표본이 충분한 기준선을 고른다. 둘 다 부족하면 null이다. */
    private static List<Integer> selectBaseline(CandidateInput input, int minSamples) {
        if (input.baselineSameSlot().size() >= minSamples) {
            return input.baselineSameSlot();
        }
        if (input.baselineSameCategory().size() >= minSamples) {
            return input.baselineSameCategory();
        }
        return null;
    }

    /** ⑤ 같은 상위 키(`카테고리|시간대`) 아래 LOW 회고가 `repeated-low-min-count` 이상. */
    private static boolean hasRepeatedLowSatisfaction(CandidateInput input, RuleParams.Candidate candidate) {
        int minCount = RuleParams.require(candidate.repeatedLowMinCount(), "rules.candidate.repeated-low-min-count");
        return input.lowSatisfactionCountInParentKey() >= minCount;
    }
}
