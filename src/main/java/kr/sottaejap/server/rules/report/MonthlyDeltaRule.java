package kr.sottaejap.server.rules.report;

import kr.sottaejap.server.common.enums.Satisfaction;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 월간 리포트 산식 (E-94 · 04 §3 MonthlySnapshot). 같은 입력이면 같은 출력이다 (NFR-01).
 *
 * <p>세 가지를 낸다 — 한 달의 집계({@link #figures}), 전월 대비 감소액({@link #savedAmount}),
 * 감소액의 목표 배분({@link #allocate}). "지난달은 확정, 이번 달은 매번 계산"은 시점 규칙이라 서비스 몫이고,
 * 여기서는 어느 달이든 같은 식으로 센다.
 */
public final class MonthlyDeltaRule {

    private MonthlyDeltaRule() {
    }

    /** {@code month}에 일어난 거래만 센다. 다른 달의 거래는 섞여 들어와도 무시한다. */
    public static MonthlyFigures figures(List<MonthlyTransaction> transactions, YearMonth month) {
        int totalSpending = 0;
        int unsatisfiedCount = 0;
        int repeatCount = 0;
        for (MonthlyTransaction transaction : transactions) {
            if (!month.equals(transaction.occurredMonth())) {
                continue;
            }
            totalSpending += transaction.amount();
            if (transaction.satisfaction() == Satisfaction.LOW) {
                unsatisfiedCount++;
            }
            if (transaction.inAdjustCluster()) {
                repeatCount++;
            }
        }
        return new MonthlyFigures(totalSpending, unsatisfiedCount, repeatCount);
    }

    /**
     * 전월 {@code totalSpending} − 당월 {@code totalSpending} (E-94 ①). 전체 지출 차라 예산 · 판정과 무관하고,
     * 더 쓴 달은 음수를 그대로 낸다. 전월 값이 없으면 null이다 — 0으로 두면 "전부 절약"으로 읽힌다.
     */
    public static Integer savedAmount(Integer previousTotalSpending, int totalSpending) {
        return previousTotalSpending == null ? null : previousTotalSpending - totalSpending;
    }

    /**
     * 감소액을 {@code expectedSaving} 비율로 정수 배분한다 (E-94 ④ · 04 §3).
     *
     * <p>각 목표는 {@code floor(savedAmount × expectedSaving ÷ Σ expectedSaving)}을 받고, 내림으로 남은 원 단위는
     * 배분액이 가장 큰 목표에 더한다. 배분액이 같으면 가중치({@code expectedSaving})가 큰 목표, 그것도 같으면
     * id가 작은(먼저 만든) 목표다 — 순서가 정해져 있어야 같은 입력에 같은 출력이 나온다. 결과는 goalId
     * 오름차순이고 0원인 목표는 넣지 않는다.
     *
     * <p>감소액이 0 이하면(더 썼거나 같으면) 배분하지 않는다. 대상이 없거나 가중치 합이 0이어도 배분하지 않는다 —
     * 나눌 비율이 없다.
     */
    public static List<GoalAllocation> allocate(int savedAmount, List<GoalSaving> savings) {
        if (savedAmount <= 0) {
            return List.of();
        }
        List<GoalSaving> ordered = savings.stream()
                .filter(saving -> saving.expectedSaving() > 0)
                .sorted(Comparator.comparingLong(GoalSaving::goalId))
                .toList();
        long weightSum = ordered.stream().mapToLong(GoalSaving::expectedSaving).sum();
        if (weightSum <= 0) {
            return List.of();
        }

        long[] amounts = new long[ordered.size()];
        long allocated = 0;
        int largest = 0;
        for (int i = 0; i < ordered.size(); i++) {
            amounts[i] = (long) savedAmount * ordered.get(i).expectedSaving() / weightSum;
            allocated += amounts[i];
            if (amounts[i] > amounts[largest]
                    || (amounts[i] == amounts[largest] && ordered.get(i).expectedSaving() > ordered.get(largest).expectedSaving())) {
                largest = i;
            }
        }
        amounts[largest] += savedAmount - allocated;

        List<GoalAllocation> allocations = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            if (amounts[i] > 0) {
                allocations.add(new GoalAllocation(ordered.get(i).goalId(), (int) amounts[i]));
            }
        }
        return List.copyOf(allocations);
    }
}
