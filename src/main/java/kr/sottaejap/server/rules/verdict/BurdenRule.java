package kr.sottaejap.server.rules.verdict;

import kr.sottaejap.server.rules.cluster.RetrospectedTransaction;

import java.time.YearMonth;
import java.util.List;

/**
 * 분석 기준월 집계 (04 §3 · B-11). 지도 가로축(burdenRatio)과 절감액 기준(avgAmount)을 여기서 만든다.
 *
 * <p>기준월은 사용자의 최근 거래월이며 사용자의 모든 묶음이 같은 달을 쓴다 (E-60).
 * 그 달 거래가 없는 묶음은 합계 0 · 건수 0 · avgAmount null이다.
 */
public final class BurdenRule {

    private BurdenRule() {
    }

    /**
     * occurredMonth == analysisYearMonth 인 거래만 집계한다 (E-60).
     *
     * <p>burdenRatio는 monthlyBudget이 null이거나 0이면 null이다 (E-61) — 합계가 0이어도 예산이 있으면 0.0이다.
     */
    public static MonthlyBurden of(List<RetrospectedTransaction> transactions,
                                   YearMonth analysisYearMonth,
                                   Integer monthlyBudget) {
        int monthlyTotalAmount = 0;
        int txCount = 0;
        for (RetrospectedTransaction transaction : transactions) {
            if (analysisYearMonth.equals(transaction.occurredMonth())) {
                monthlyTotalAmount += transaction.amount();
                txCount++;
            }
        }

        Integer avgAmount = txCount == 0 ? null : monthlyTotalAmount / txCount;
        Double burdenRatio = (monthlyBudget == null || monthlyBudget == 0)
                ? null
                : (double) monthlyTotalAmount / monthlyBudget;

        return new MonthlyBurden(monthlyTotalAmount, txCount, avgAmount, burdenRatio);
    }
}
