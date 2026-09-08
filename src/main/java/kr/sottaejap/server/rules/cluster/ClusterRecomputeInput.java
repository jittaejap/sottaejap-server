package kr.sottaejap.server.rules.cluster;

import java.time.YearMonth;
import java.util.List;

/**
 * 사용자 한 명의 전체 재계산 입력 (E-61).
 *
 * @param monthlyBudget     월 예산 — null 또는 0이면 burdenRatio·quadrant는 null
 * @param analysisYearMonth 사용자의 최근 거래월 (E-60) — 서비스가 구해서 넘긴다
 * @param transactions      회고가 붙은 거래 전부
 */
public record ClusterRecomputeInput(
        Integer monthlyBudget,
        YearMonth analysisYearMonth,
        List<RetrospectedTransaction> transactions
) {
}
