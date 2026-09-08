package kr.sottaejap.server.report.dto;

import kr.sottaejap.server.rules.report.GoalAllocation;

import java.util.List;

/**
 * {@code GET /reports/monthly} (05 §2 #17 · E-94).
 *
 * @param yearMonth              `2026-08`
 * @param finalized              지난달(스냅샷에서 읽음)이면 true, 이번 달(매번 계산 · 미저장)이면 false
 * @param totalSpending          그 달 거래 금액 합. 거래가 없어도 0이다 — 404 없음
 * @param previousTotalSpending  전월 합. 전월 데이터가 없으면 null
 * @param savedAmount            previousTotalSpending − totalSpending. 음수 허용, 전월 없으면 null
 * @param unsatisfiedCount       그 달 거래의 회고 중 LOW 건수 (FR-08-07)
 * @param repeatCount            유효 묶음 ∧ RESOLVED ∧ ADJUST 묶음의 그 달 거래 건수 합 (FR-08-07)
 * @param previousRepeatCount    전월의 repeatCount. 전월 데이터가 없으면 null
 * @param goalAllocations        이 요청에서 확정 · 배분이 일어났을 때만 채워진다. 그 외에는 빈 배열
 */
public record MonthlyReportResponse(
        String yearMonth,
        boolean finalized,
        int totalSpending,
        Integer previousTotalSpending,
        Integer savedAmount,
        int unsatisfiedCount,
        int repeatCount,
        Integer previousRepeatCount,
        List<GoalAllocationView> goalAllocations
) {

    public record GoalAllocationView(long goalId, int amount) {

        public static GoalAllocationView from(GoalAllocation allocation) {
            return new GoalAllocationView(allocation.goalId(), allocation.amount());
        }
    }
}
