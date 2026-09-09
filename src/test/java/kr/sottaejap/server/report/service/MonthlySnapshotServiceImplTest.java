package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.goal.dto.GoalListResponse;
import kr.sottaejap.server.goal.dto.GoalView;
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
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 시점 규칙 — 지난달 이전은 첫 조회 때 확정 · 이번 달은 미저장 · 직전 달 확정 시 1회 배분 · 확정된 달은 전월 값까지
 * 굳는다 (E-94 ③ ④). 산식 자체는 {@code MonthlyDeltaRuleTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class MonthlySnapshotServiceImplTest {

    private static final long USER_ID = 1L;
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-01T10:00:00+09:00");

    @Mock
    private MonthlySnapshotRepository monthlySnapshotRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RetrospectRepository retrospectRepository;
    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private GoalService goalService;
    @Mock
    private GoalRepository goalRepository;

    private MonthlySnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MonthlySnapshotServiceImpl(monthlySnapshotRepository, transactionRepository,
                retrospectRepository, behaviorClusterRepository, goalService, goalRepository);
        lenient().when(monthlySnapshotRepository.findByUserIdAndYearMonth(anyLong(), anyString())).thenReturn(Optional.empty());
        lenient().when(retrospectRepository.findAllByTransactionIdIn(any())).thenReturn(List.of());
        lenient().when(behaviorClusterRepository.findEffectiveByUserId(USER_ID)).thenReturn(List.of());
    }

    @Test
    void 이번_달은_계산만_하고_저장하지_않는다() {
        givenTransactions(
                transaction(1L, "2026-08-10T12:00:00+09:00", 60_000, null),
                transaction(2L, "2026-09-03T12:00:00+09:00", 40_000, null));

        MonthlyReportResponse response = service.monthly(USER_ID, SEPTEMBER, SEPTEMBER);

        assertFalse(response.finalized());
        assertEquals(40_000, response.totalSpending());
        assertEquals(60_000, response.previousTotalSpending());
        assertEquals(20_000, response.savedAmount());
        assertTrue(response.goalAllocations().isEmpty());
        verify(monthlySnapshotRepository, never()).insertIfAbsent(anyLong(), anyString(), anyInt(), anyInt(), anyInt(), any());
        verifyNoInteractions(goalService, goalRepository);
    }

    @Test
    void 직전_달은_첫_조회_때_계산해_저장하고_감소액을_DB에서_원자적으로_배분한다() {
        givenTransactions(
                transaction(1L, "2026-07-10T12:00:00+09:00", 60_000, null),
                transaction(2L, "2026-08-20T23:10:00+09:00", 12_000, 10L),
                transaction(3L, "2026-08-21T23:20:00+09:00", 15_000, 10L),
                transaction(4L, "2026-08-22T09:00:00+09:00", 4_000, null));
        when(retrospectRepository.findAllByTransactionIdIn(any())).thenReturn(List.of(
                retrospect(2L, Satisfaction.LOW), retrospect(3L, Satisfaction.LOW), retrospect(4L, Satisfaction.HIGH)));
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID))
                .thenReturn(List.of(cluster(10L, EvaluationStatus.RESOLVED, Verdict.ADJUST)));
        when(behaviorClusterRepository.findAllByParentIdIn(any())).thenReturn(List.of());
        when(monthlySnapshotRepository.insertIfAbsent(USER_ID, "2026-08", 31_000, 2, 2, 29_000)).thenReturn(1);
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.empty(), Optional.of(snapshot("2026-08", 31_000, 2, 2, 29_000)));
        // 목표 둘 — 채택 절감액 3 : 1. 세 번째 목표는 채택 제안이 없어 배분 대상이 아니다
        when(goalService.list(USER_ID)).thenReturn(new GoalListResponse(List.of(
                new GoalView(1L, "여행", 1_000_000, 100_000, 30_000, 0.1, 0.13),
                new GoalView(2L, "노트북", 500_000, 0, 10_000, 0.0, 0.02),
                new GoalView(3L, "비상금", 300_000, 0, 0, 0.0, 0.0))));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertTrue(response.finalized());
        assertEquals(31_000, response.totalSpending());
        assertEquals(60_000, response.previousTotalSpending());
        assertEquals(29_000, response.savedAmount());
        assertEquals(2, response.unsatisfiedCount());
        assertEquals(2, response.repeatCount());
        // 7월 스냅샷이 없으니 전월 반복 횟수는 비워 둔다 — 지금 거래로 세면 나중에 흔들린다
        assertNull(response.previousRepeatCount());
        assertEquals(List.of(new GoalAllocationView(1L, 21_750), new GoalAllocationView(2L, 7_250)),
                response.goalAllocations());
        // 엔티티를 읽어 더하지 않고 DB에서 더한다 — 다른 달을 동시에 확정해도 앞의 배분이 증발하지 않는다
        verify(goalRepository).addCurrentAmount(1L, 21_750);
        verify(goalRepository).addCurrentAmount(2L, 7_250);
        verify(goalRepository, never()).findAllById(any());
    }

    @Test
    void 상위_묶음이_조정_대상이면_그_리프에_배정된_거래도_반복_횟수에_든다() {
        givenTransactions(
                transaction(2L, "2026-08-20T23:10:00+09:00", 12_000, 11L),
                transaction(3L, "2026-08-21T23:20:00+09:00", 15_000, 12L));
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID))
                .thenReturn(List.of(cluster(10L, EvaluationStatus.RESOLVED, Verdict.ADJUST),
                        cluster(20L, EvaluationStatus.RESOLVED, Verdict.SUSTAIN)));
        // 10의 자식 리프 11 — 12는 SUSTAIN 묶음(20)의 자식이라 대상이 아니다
        when(behaviorClusterRepository.findAllByParentIdIn(Set.of(10L)))
                .thenReturn(List.of(cluster(11L, EvaluationStatus.PENDING, null)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, AUGUST);

        assertEquals(1, response.repeatCount());
    }

    @Test
    void 이미_확정된_달은_저장된_값만_돌려주고_거래_회고_묶음을_읽지_않는다() {
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.of(snapshot("2026-08", 31_000, 2, 2, 29_000)));
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-07"))
                .thenReturn(Optional.of(snapshot("2026-07", 60_000, 0, 5, null)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertTrue(response.finalized());
        assertEquals(31_000, response.totalSpending());
        assertEquals(29_000, response.savedAmount());
        // 전월 합은 저장된 감소액에서 역산 — 31,000 + 29,000
        assertEquals(60_000, response.previousTotalSpending());
        assertEquals(5, response.previousRepeatCount());
        assertTrue(response.goalAllocations().isEmpty());
        verifyNoInteractions(transactionRepository, retrospectRepository, behaviorClusterRepository, goalService, goalRepository);
    }

    @Test
    void 확정된_달의_전월_값은_지금_거래로_다시_세지_않는다() {
        // 8월은 전월 없이(savedAmount null) 확정됐고, 그 뒤 7월 거래가 올라왔다 — 7월 스냅샷은 아직 없다
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.of(snapshot("2026-08", 31_000, 0, 0, null)));
        lenient().when(transactionRepository.findAllInRange(eq(USER_ID), any(), any()))
                .thenReturn(List.of(transaction(1L, "2026-07-10T12:00:00+09:00", 60_000, 10L)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        // previousTotalSpending − totalSpending = savedAmount 가 응답 안에서 성립해야 한다 — 60,000이 끼어들면 깨진다
        assertNull(response.savedAmount());
        assertNull(response.previousTotalSpending());
        assertNull(response.previousRepeatCount());
        verifyNoInteractions(transactionRepository, retrospectRepository, behaviorClusterRepository);
    }

    @Test
    void 전월_데이터가_없으면_감소액과_전월_값은_null이고_배분도_없다() {
        givenTransactions(transaction(1L, "2026-08-10T12:00:00+09:00", 60_000, null));
        when(monthlySnapshotRepository.insertIfAbsent(USER_ID, "2026-08", 60_000, 0, 0, null)).thenReturn(1);
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.empty(), Optional.of(snapshot("2026-08", 60_000, 0, 0, null)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertTrue(response.finalized());
        assertEquals(60_000, response.totalSpending());
        assertNull(response.previousTotalSpending());
        assertNull(response.savedAmount());
        assertNull(response.previousRepeatCount());
        verifyNoInteractions(goalService, goalRepository);
    }

    @Test
    void 거래가_없는_달도_200이고_전부_0이다() {
        givenTransactions();

        MonthlyReportResponse response = service.monthly(USER_ID, SEPTEMBER, SEPTEMBER);

        assertEquals(0, response.totalSpending());
        assertNull(response.previousTotalSpending());
        assertNull(response.savedAmount());
        assertEquals(0, response.unsatisfiedCount());
        assertEquals(0, response.repeatCount());
        verifyNoInteractions(retrospectRepository, behaviorClusterRepository);
    }

    @Test
    void 더_쓴_달은_음수를_그대로_내고_배분하지_않는다() {
        givenTransactions(
                transaction(1L, "2026-07-10T12:00:00+09:00", 40_000, null),
                transaction(2L, "2026-08-10T12:00:00+09:00", 60_000, null));
        when(monthlySnapshotRepository.insertIfAbsent(USER_ID, "2026-08", 60_000, 0, 0, -20_000)).thenReturn(1);
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.empty(), Optional.of(snapshot("2026-08", 60_000, 0, 0, -20_000)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertEquals(-20_000, response.savedAmount());
        assertTrue(response.goalAllocations().isEmpty());
        verifyNoInteractions(goalService, goalRepository);
    }

    @Test
    void 동시에_들어온_첫_조회에서_진_쪽은_배분하지_않고_이긴_쪽의_값을_돌려준다() {
        givenTransactions(
                transaction(1L, "2026-07-10T12:00:00+09:00", 60_000, null),
                transaction(2L, "2026-08-10T12:00:00+09:00", 31_000, null));
        when(monthlySnapshotRepository.insertIfAbsent(USER_ID, "2026-08", 31_000, 0, 0, 29_000)).thenReturn(0);
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.empty(), Optional.of(snapshot("2026-08", 31_000, 0, 0, 29_000)));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertTrue(response.finalized());
        assertEquals(29_000, response.savedAmount());
        assertTrue(response.goalAllocations().isEmpty());
        verifyNoInteractions(goalService, goalRepository);
    }

    @Test
    void 붙은_목표가_없으면_확정만_하고_배분하지_않는다() {
        givenTransactions(
                transaction(1L, "2026-07-10T12:00:00+09:00", 60_000, null),
                transaction(2L, "2026-08-10T12:00:00+09:00", 31_000, null));
        when(monthlySnapshotRepository.insertIfAbsent(USER_ID, "2026-08", 31_000, 0, 0, 29_000)).thenReturn(1);
        when(monthlySnapshotRepository.findByUserIdAndYearMonth(USER_ID, "2026-08"))
                .thenReturn(Optional.empty(), Optional.of(snapshot("2026-08", 31_000, 0, 0, 29_000)));
        when(goalService.list(USER_ID)).thenReturn(new GoalListResponse(List.of(
                new GoalView(3L, "비상금", 300_000, 0, 0, 0.0, 0.0))));

        MonthlyReportResponse response = service.monthly(USER_ID, AUGUST, SEPTEMBER);

        assertTrue(response.goalAllocations().isEmpty());
        verifyNoInteractions(goalRepository);
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepository.findAllInRange(eq(USER_ID), any(), any())).thenReturn(List.of(transactions));
    }

    private static Transaction transaction(long id, String occurredAt, int amount, Long behaviorId) {
        Transaction transaction = Transaction.of(USER_ID, OffsetDateTime.parse(occurredAt), "가맹점", amount, "배달", "배달", "hash-" + id);
        ReflectionTestUtils.setField(transaction, "id", id);
        if (behaviorId != null) {
            transaction.assignBehavior(behaviorId);
        }
        return transaction;
    }

    private static Retrospect retrospect(long transactionId, Satisfaction satisfaction) {
        return Retrospect.completed(transactionId, satisfaction, null, null, null, RetrospectSource.CANDIDATE, NOW);
    }

    private static BehaviorCluster cluster(long id, EvaluationStatus status, Verdict verdict) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, "배달|NIGHT||");
        ReflectionTestUtils.setField(cluster, "id", id);
        ReflectionTestUtils.setField(cluster, "evaluationStatus", status);
        ReflectionTestUtils.setField(cluster, "verdict", verdict);
        return cluster;
    }

    /** 엔티티에 생성 경로가 없다 — 행은 native insert가 만든다. 테스트만 리플렉션으로 채운다. */
    private static MonthlySnapshot snapshot(String yearMonth, int total, int unsatisfied, int repeat, Integer saved) {
        MonthlySnapshot snapshot = BeanUtils.instantiateClass(MonthlySnapshot.class);
        ReflectionTestUtils.setField(snapshot, "userId", USER_ID);
        ReflectionTestUtils.setField(snapshot, "yearMonth", yearMonth);
        ReflectionTestUtils.setField(snapshot, "totalSpending", total);
        ReflectionTestUtils.setField(snapshot, "unsatisfiedCount", unsatisfied);
        ReflectionTestUtils.setField(snapshot, "repeatCount", repeat);
        ReflectionTestUtils.setField(snapshot, "savedAmount", saved);
        return snapshot;
    }
}
