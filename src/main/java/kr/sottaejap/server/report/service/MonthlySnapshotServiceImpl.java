package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.goal.service.GoalService;
import kr.sottaejap.server.report.domain.MonthlySnapshot;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.report.dto.MonthlyReportResponse.GoalAllocationView;
import kr.sottaejap.server.report.repository.MonthlySnapshotRepository;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.rules.report.GoalAllocation;
import kr.sottaejap.server.rules.report.GoalSaving;
import kr.sottaejap.server.rules.report.MonthlyDeltaRule;
import kr.sottaejap.server.rules.report.MonthlyFigures;
import kr.sottaejap.server.rules.report.MonthlyTransaction;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 월간 리포트 (E-94 · 04 §3 MonthlySnapshot).
 *
 * <p><b>지난달 이전은 첫 조회 때 확정, 이번 달은 매번 계산.</b> 스냅샷이 있으면 그대로 돌려주고 거래 · 회고 · 묶음을
 * 읽지도 않는다 — 회고를 더할 때마다 지난달 숫자가 흔들리면 안 된다. 없으면 계산해 넣는다. 동시에 들어온 첫 조회
 * 둘은 {@link MonthlySnapshotRepository#insertIfAbsent}가 가른다.
 *
 * <p><b>실적 배분은 직전 달(현재 연월 − 1)이 확정되는 요청에서만</b> 한다 (E-94 ④). 그 이전 달은 확정만 하고
 * 실적을 올리지 않는다 — 어느 과거 달이든 배분하면 달성률이 "얼마나 아꼈나"가 아니라 "과거 달을 몇 개 열어봤나"에
 * 좌우된다. 배분은 {@link GoalRepository#addCurrentAmount}로 DB에서 원자적으로 더한다.
 *
 * <p><b>확정된 달의 전월 값도 굳어 있다.</b> {@code previousTotalSpending}은 저장된 {@code savedAmount}에서 역산하고
 * (04 §3의 식 {@code savedAmount = previousTotalSpending − totalSpending}이 응답 안에서 항상 성립한다),
 * {@code previousRepeatCount}는 전월 스냅샷이 있으면 그 값, 없으면 null이다 — 전월이 나중에 확정되면 한 번 채워지고
 * 그 뒤로는 움직이지 않는다. 지금 거래로 다시 세면 전월 회고 하나에 확정된 달의 응답이 바뀐다.
 *
 * <p>확정하지 않은 달(이번 달)의 전월 값은 전월 스냅샷이 있으면 그것, 없으면 지금 거래로 계산하고
 * 저장하지 않는다. 전월에 거래도 스냅샷도 없으면 "전월 없음"이라 {@code savedAmount}가 null이다.
 */
@Service
@RequiredArgsConstructor
public class MonthlySnapshotServiceImpl implements MonthlySnapshotService {

    private final MonthlySnapshotRepository monthlySnapshotRepository;
    private final TransactionRepository transactionRepository;
    private final RetrospectRepository retrospectRepository;
    private final BehaviorClusterRepository behaviorClusterRepository;
    private final GoalService goalService;
    private final GoalRepository goalRepository;

    @Override
    @Transactional
    public MonthlyReportResponse monthly(long userId, YearMonth month, YearMonth currentMonth) {
        YearMonth previousMonth = month.minusMonths(1);
        Optional<MonthlySnapshot> previousStored =
                monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(previousMonth));
        Optional<MonthlySnapshot> stored = monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(month));
        if (stored.isPresent()) {
            return finalizedResponse(stored.get(), previousStored, List.of());
        }

        List<MonthlyTransaction> transactions = load(userId, previousMonth, month);
        MonthlyFigures figures = MonthlyDeltaRule.figures(transactions, month);
        Previous previous = previousStored.map(Previous::of)
                .orElseGet(() -> Previous.computed(transactions, previousMonth));
        Integer savedAmount = MonthlyDeltaRule.savedAmount(previous.totalSpending(), figures.totalSpending());

        if (!month.isBefore(currentMonth)) {
            return new MonthlyReportResponse(MonthlySnapshot.text(month), false,
                    figures.totalSpending(), previous.totalSpending(), savedAmount,
                    figures.unsatisfiedCount(), figures.repeatCount(), previous.repeatCount(), List.of());
        }

        int inserted = monthlySnapshotRepository.insertIfAbsent(userId, MonthlySnapshot.text(month),
                figures.totalSpending(), figures.unsatisfiedCount(), figures.repeatCount(), savedAmount);
        boolean allocatable = inserted == 1 && month.equals(currentMonth.minusMonths(1));
        List<GoalAllocation> allocations = allocatable ? allocate(userId, savedAmount) : List.of();
        MonthlySnapshot snapshot = monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(month))
                .orElseThrow(() -> new IllegalStateException("방금 넣었거나 다른 요청이 넣은 스냅샷이 없습니다: " + month));
        return finalizedResponse(snapshot, previousStored, allocations);
    }

    /**
     * 두 달치 거래에 회고 만족도와 조정 대상 여부를 붙인다. 조정 대상 묶음(유효 ∧ RESOLVED ∧ ADJUST)은
     * {@link BehaviorClusterRepository#findEffectiveByUserId}로 고정된 유효 묶음 필터를 그대로 쓰고, 상위 묶음에는
     * 직접 구성원만 배정돼 있어(E-59) 자식 리프까지 같이 본다 — {@code BehaviorServiceImpl}의 상세 조회와 같다.
     */
    private List<MonthlyTransaction> load(long userId, YearMonth from, YearMonth to) {
        List<Transaction> transactions = transactionRepository.findAllInRange(userId, startOf(from), startOf(to.plusMonths(1)));
        if (transactions.isEmpty()) {
            return List.of();
        }
        Map<Long, Satisfaction> satisfactions = retrospectRepository
                .findAllByTransactionIdIn(transactions.stream().map(Transaction::getId).toList()).stream()
                .collect(Collectors.toMap(Retrospect::getTransactionId, Retrospect::getSatisfaction));
        Set<Long> adjustClusterIds = adjustClusterIds(userId);

        return transactions.stream()
                .map(transaction -> new MonthlyTransaction(
                        transaction.getAmount(),
                        YearMonth.from(transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE)),
                        satisfactions.get(transaction.getId()),
                        transaction.getBehaviorId() != null && adjustClusterIds.contains(transaction.getBehaviorId())))
                .toList();
    }

    private Set<Long> adjustClusterIds(long userId) {
        Set<Long> roots = behaviorClusterRepository.findEffectiveByUserId(userId).stream()
                .filter(cluster -> cluster.getEvaluationStatus() == EvaluationStatus.RESOLVED
                        && cluster.getVerdict() == Verdict.ADJUST)
                .map(BehaviorCluster::getId)
                .collect(Collectors.toSet());
        Set<Long> members = new HashSet<>(roots);
        if (!roots.isEmpty()) {
            behaviorClusterRepository.findAllByParentIdIn(roots).forEach(child -> members.add(child.getId()));
        }
        return members;
    }

    /**
     * 확정 시 1회 배분 (E-94 ④). 대상은 ADOPTED 제안이 붙은 목표이고 가중치는 그 목표의 {@code adoptedSaving}
     * ({@code GET /goals}가 세는 값과 같은 것, E-83)이다. 삭제한 목표는 목록에 없으므로 배분에서도 빠진다.
     */
    private List<GoalAllocation> allocate(long userId, Integer savedAmount) {
        if (savedAmount == null || savedAmount <= 0) {
            return List.of();
        }
        List<GoalSaving> savings = goalService.list(userId).goals().stream()
                .filter(goal -> goal.adoptedSaving() > 0)
                .map(goal -> new GoalSaving(goal.id(), goal.adoptedSaving()))
                .toList();
        List<GoalAllocation> allocations = MonthlyDeltaRule.allocate(savedAmount, savings);
        for (GoalAllocation allocation : allocations) {
            goalRepository.addCurrentAmount(allocation.goalId(), allocation.amount());
        }
        return allocations;
    }

    /** 확정된 달 — 세 숫자는 저장값이고 전월 값도 거기서만 나온다. */
    private static MonthlyReportResponse finalizedResponse(MonthlySnapshot snapshot, Optional<MonthlySnapshot> previousStored,
                                                           List<GoalAllocation> allocations) {
        Integer previousTotalSpending = snapshot.getSavedAmount() == null
                ? null
                : snapshot.getTotalSpending() + snapshot.getSavedAmount();
        Integer previousRepeatCount = previousStored.map(MonthlySnapshot::getRepeatCount).orElse(null);
        return new MonthlyReportResponse(snapshot.getYearMonth(), true,
                snapshot.getTotalSpending(), previousTotalSpending, snapshot.getSavedAmount(),
                snapshot.getUnsatisfiedCount(), snapshot.getRepeatCount(), previousRepeatCount,
                allocations.stream().map(GoalAllocationView::from).toList());
    }

    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    /** 확정하지 않은 달의 전월 값. 둘 다 null이면 "전월 없음"이다. */
    private record Previous(Integer totalSpending, Integer repeatCount) {

        static Previous of(MonthlySnapshot snapshot) {
            return new Previous(snapshot.getTotalSpending(), snapshot.getRepeatCount());
        }

        /** 전월 거래가 한 건도 없으면 0이 아니라 "없음"이다 — 0으로 두면 이번 달 지출 전부가 '더 쓴 돈'이 된다. */
        static Previous computed(List<MonthlyTransaction> transactions, YearMonth previousMonth) {
            boolean hasData = transactions.stream().anyMatch(transaction -> previousMonth.equals(transaction.occurredMonth()));
            if (!hasData) {
                return new Previous(null, null);
            }
            MonthlyFigures figures = MonthlyDeltaRule.figures(transactions, previousMonth);
            return new Previous(figures.totalSpending(), figures.repeatCount());
        }
    }
}
