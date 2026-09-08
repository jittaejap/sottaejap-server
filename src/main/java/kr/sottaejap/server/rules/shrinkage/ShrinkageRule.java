package kr.sottaejap.server.rules.shrinkage;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.rules.RuleParams;

import java.util.List;

/**
 * 축소 추정 보정 (04 §3 · E-61). 회고가 적은 묶음의 평균을 사용자 전체 평균 쪽으로 끌어당긴다.
 *
 * <p>분모는 전부 <b>UNKNOWN 제외 건수</b>다 (B-2 · E-23). 보류 판정(evaluationStatus)의 건수는
 * UNKNOWN을 포함하므로 서로 다른 수임에 주의한다 (E-61).
 */
public final class ShrinkageRule {

    private ShrinkageRule() {
    }

    /**
     * 묶음의 원 평균 = Σ(HIGH:+1, LOW:−1) ÷ (UNKNOWN 제외 수).
     *
     * @return UNKNOWN 제외 표본이 0이면 null (E-23 · E-61)
     */
    public static Double rawAverage(List<Satisfaction> satisfactions) {
        int scored = scoredCount(satisfactions);
        if (scored == 0) {
            return null;
        }
        return (double) scoreSum(satisfactions) / scored;
    }

    /**
     * 사용자 전체 평균 (User.avgSatisfaction 캐시 값). 산식은 {@link #rawAverage}와 같지만
     * 표본이 0이면 척도 중립값 0.0이다 — 첫 회고 사용자의 보정 기준 (E-61).
     */
    public static double userAverage(List<Satisfaction> satisfactions) {
        int scored = scoredCount(satisfactions);
        if (scored == 0) {
            return 0.0;
        }
        return (double) scoreSum(satisfactions) / scored;
    }

    /** UNKNOWN 제외 건수 — rawAverage의 분모이자 축소 추정의 n (E-61). */
    public static int scoredCount(List<Satisfaction> satisfactions) {
        int scored = 0;
        for (Satisfaction satisfaction : satisfactions) {
            if (satisfaction != Satisfaction.UNKNOWN) {
                scored++;
            }
        }
        return scored;
    }

    /**
     * adjusted = (n × rawAverage + k × userAverage) ÷ (n + k). n은 UNKNOWN 제외 건수 (E-61).
     *
     * <p>rawAverage가 null(전부 UNKNOWN)이면 userAverage를 그대로 돌려준다 (04 §3).
     * k가 비어 있으면 기본값으로 대체하지 않고 계산을 거부한다 (E-18 · E-57).
     */
    public static double adjust(int scoredCount, Double rawAverage, double userAverage, RuleParams params) {
        double k = RuleParams.require(params.shrinkageK(), "rules.shrinkage-k");
        if (rawAverage == null) {
            return userAverage;
        }
        return (scoredCount * rawAverage + k * userAverage) / (scoredCount + k);
    }

    /** HIGH +1 · LOW −1 · UNKNOWN 0 (B-2). */
    private static int scoreSum(List<Satisfaction> satisfactions) {
        int sum = 0;
        for (Satisfaction satisfaction : satisfactions) {
            if (satisfaction == Satisfaction.HIGH) {
                sum++;
            } else if (satisfaction == Satisfaction.LOW) {
                sum--;
            }
        }
        return sum;
    }
}
