package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.goal.domain.Goal;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 월간 리포트 (E-94 · 04 §3 MonthlySnapshot).
 *
 * <p><b>지난달은 첫 조회 때 확정, 이번 달은 매번 계산.</b> 지난달 스냅샷이 있으면 그대로 돌려주고 다시 계산하지
 * 않는다 — 회고를 더할 때마다 지난달 숫자가 흔들리면 안 된다. 없으면 계산해 넣고, 그 요청에서만 {@code savedAmount > 0}을
 * ADOPTED 제안이 붙은 목표에 배분한다. 확정과 배분은 이 클래스의 {@code @Transactional} 하나 안이라 두 번 더해지지
 * 않는다. 동시에 들어온 첫 조회 둘은 {@link MonthlySnapshotRepository#insertIfAbsent}가 가른다.
 *
 * <p>전월 값은 <b>전월 스냅샷이 있으면 그것</b>이고, 없으면 지금 거래로 계산하되 저장하지 않는다 — 전월을 확정하는
 * 것은 전월을 조회하는 요청이다. 전월에 거래도 스냅샷도 없으면 "전월 없음"이라 {@code savedAmount}가 null이다.
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
        Optional<MonthlySnapshot> stored = monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(month));
        Optional<MonthlySnapshot> previousStored =
                monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(previousMonth));

        // 전월 스냅샷이 있으면 거래는 이번 달만 필요하지만, 두 달치 거래를 한 번에 읽는 편이 질의가 적다.
        List<MonthlyTransaction> transactions = load(userId, previousMonth, month);
        Previous previous = previousStored.map(Previous::of)
                .orElseGet(() -> Previous.computed(transactions, previousMonth));

        if (stored.isPresent()) {
            return finalizedResponse(stored.get(), previous, List.of());
        }

        MonthlyFigures figures = MonthlyDeltaRule.figures(transactions, month);
        Integer savedAmount = MonthlyDeltaRule.savedAmount(previous.totalSpending(), figures.totalSpending());
        if (!month.isBefore(currentMonth)) {
            return new MonthlyReportResponse(MonthlySnapshot.text(month), false,
                    figures.totalSpending(), previous.totalSpending(), savedAmount,
                    figures.unsatisfiedCount(), figures.repeatCount(), previous.repeatCount(), List.of());
        }

        int inserted = monthlySnapshotRepository.insertIfAbsent(userId, MonthlySnapshot.text(month),
                figures.totalSpending(), figures.unsatisfiedCount(), figures.repeatCount(), savedAmount);
        List<GoalAllocation> allocations = inserted == 1 ? allocate(userId, savedAmount) : List.of();
        MonthlySnapshot snapshot = monthlySnapshotRepository.findByUserIdAndYearMonth(userId, MonthlySnapshot.text(month))
                .orElseThrow(() -> new IllegalStateException("방금 넣었거나 다른 요청이 넣은 스냅샷이 없습니다: " + month));
        return finalizedResponse(snapshot, previous, allocations);
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
        Map<Long, Goal> goals = goalRepository.findAllById(allocations.stream().map(GoalAllocation::goalId).toList()).stream()
                .collect(Collectors.toMap(Goal::getId, Function.identity()));
        for (GoalAllocation allocation : allocations) {
            goals.get(allocation.goalId()).addCurrentAmount(allocation.amount());
        }
        return allocations;
    }

    private static MonthlyReportResponse finalizedResponse(MonthlySnapshot snapshot, Previous previous,
                                                           List<GoalAllocation> allocations) {
        return new MonthlyReportResponse(snapshot.getYearMonth(), true,
                snapshot.getTotalSpending(), previous.totalSpending(), snapshot.getSavedAmount(),
                snapshot.getUnsatisfiedCount(), snapshot.getRepeatCount(), previous.repeatCount(),
                allocations.stream().map(GoalAllocationView::from).toList());
    }

    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    /** 전월 값. 둘 다 null이면 "전월 없음"이다. */
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
