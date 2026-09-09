package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.service.GoalService;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.report.dto.MonthlyReportResponse.GoalAllocationView;
import kr.sottaejap.server.report.repository.MonthlySnapshotRepository;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.service.RetrospectWriter;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.service.SuggestionService;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 거래 · 회고 · 제안 채택 · 목표 위에서 월간 리포트를 실제 PostgreSQL로 돌린다 (E-94 · 04 §3).
 *
 * <p>핵심 주장 넷 — 지난달 이전은 첫 조회 때 확정되고 그 뒤 회고를 더해도 전월 값까지 숫자가 그대로다, 목표 실적은
 * 직전 달이 확정되는 그 요청에서 딱 한 번 오른다, 그 이전 달은 확정만 한다, 이번 달은 저장하지 않는다.
 * "지금"은 2026-09-09로 고정한다 — 8월이 직전 달, 7월이 두 달 전이다. 예산이 없는 사용자다 —
 * {@code savedAmount}는 예산과 무관해야 한다. 테스트 트랜잭션은 롤백된다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class MonthlyReportIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), TimeSlot.ZONE);
        }
    }

    private long userId;

    @Autowired
    private ReportService reportService;
    @Autowired
    private MonthlySnapshotRepository monthlySnapshotRepository;
    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SuggestionService suggestionService;
    @Autowired
    private GoalService goalService;

    @BeforeEach
    void createIsolatedUserWithoutBudget() {
        User user = User.social(AuthProvider.KAKAO, "report-it-" + System.nanoTime(), "통합테스트", null);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 직전_달은_첫_조회_때_확정되고_목표_실적이_한_번만_오른다() {
        // 7월 60,000 (회고 없음) · 8월 36,000 LOW 회고 3건 + 회고 없는 4,000 = 40,000
        save("2026-07-05T12:00:00+09:00", 25_000);
        save("2026-07-15T12:00:00+09:00", 35_000);
        writeThreeLowRetrospects("2026-08");
        save("2026-08-25T09:00:00+09:00", 4_000);
        GoalView goal = goalService.create(userId, new GoalRequest("여행 자금", 1_000_000, 0));
        adoptFirstSuggestion(goal);

        MonthlyReportResponse first = reportService.monthly(userId, AUGUST);

        assertTrue(first.finalized());
        assertEquals(40_000, first.totalSpending());
        assertEquals(60_000, first.previousTotalSpending());
        assertEquals(20_000, first.savedAmount());
        assertEquals(3, first.unsatisfiedCount());
        assertEquals(3, first.repeatCount());
        // 7월 스냅샷이 없으니 전월 반복 횟수는 비워 둔다
        assertNull(first.previousRepeatCount());
        assertEquals(List.of(new GoalAllocationView(goal.id(), 20_000)), first.goalAllocations());
        GoalView afterFinalize = goalService.list(userId).goals().getFirst();
        assertEquals(20_000, afterFinalize.currentAmount());
        assertEquals(0.02, afterFinalize.achievementRate(), 1e-9);
        assertTrue(monthlySnapshotRepository.findByUserIdAndYearMonth(userId, "2026-08").isPresent());

        // 확정 뒤 회고를 하나 더 저장해 재계산이 돌아도 지난달 숫자는 그대로고, 실적도 두 번 오르지 않는다
        retrospectWriter.write(userId, request(save("2026-08-23T23:40:00+09:00", 30_000)));
        MonthlyReportResponse second = reportService.monthly(userId, AUGUST);

        assertEquals(first.totalSpending(), second.totalSpending());
        assertEquals(first.previousTotalSpending(), second.previousTotalSpending());
        assertEquals(first.savedAmount(), second.savedAmount());
        assertEquals(first.unsatisfiedCount(), second.unsatisfiedCount());
        assertEquals(first.repeatCount(), second.repeatCount());
        assertEquals(first.previousRepeatCount(), second.previousRepeatCount());
        assertTrue(second.goalAllocations().isEmpty());
        assertEquals(20_000, goalService.list(userId).goals().getFirst().currentAmount());
    }

    @Test
    void 직전_달이_아닌_과거_달은_확정만_하고_실적을_올리지_않는다() {
        // 6월 60,000 · 7월 LOW 회고 3건 36,000 — 7월은 두 달 전이다
        save("2026-06-05T12:00:00+09:00", 60_000);
        writeThreeLowRetrospects("2026-07");
        GoalView goal = goalService.create(userId, new GoalRequest("여행 자금", 1_000_000, 0));
        adoptFirstSuggestion(goal);

        MonthlyReportResponse july = reportService.monthly(userId, JULY);

        assertTrue(july.finalized());
        assertEquals(24_000, july.savedAmount());
        assertTrue(july.goalAllocations().isEmpty());
        assertEquals(0, goalService.list(userId).goals().getFirst().currentAmount());
    }

    @Test
    void 확정된_달의_전월_값은_전월이_확정될_때_한_번_채워지고_그_뒤로_굳는다() {
        // 7월 LOW 회고 3건(조정 대상 묶음 · 반복 3회) · 8월 회고 없는 4,000
        writeThreeLowRetrospects("2026-07");
        save("2026-08-25T09:00:00+09:00", 4_000);

        MonthlyReportResponse augustFirst = reportService.monthly(userId, AUGUST);
        assertEquals(36_000, augustFirst.previousTotalSpending());
        assertEquals(32_000, augustFirst.savedAmount());
        assertNull(augustFirst.previousRepeatCount());

        // 7월을 확정하면 8월의 전월 반복 횟수가 채워진다
        assertEquals(3, reportService.monthly(userId, JULY).repeatCount());
        assertEquals(3, reportService.monthly(userId, AUGUST).previousRepeatCount());

        // 7월 회고를 더 저장해 반복 횟수가 4가 됐어도 확정된 두 달은 그대로다
        retrospectWriter.write(userId, request(save("2026-07-23T23:40:00+09:00", 30_000)));
        MonthlyReportResponse julyAgain = reportService.monthly(userId, JULY);
        MonthlyReportResponse augustAgain = reportService.monthly(userId, AUGUST);

        assertEquals(3, julyAgain.repeatCount());
        assertEquals(36_000, julyAgain.totalSpending());
        assertEquals(3, augustAgain.previousRepeatCount());
        assertEquals(36_000, augustAgain.previousTotalSpending());
        assertEquals(32_000, augustAgain.savedAmount());
    }

    @Test
    void 전월_데이터가_없는_달은_감소액_없이_확정되고_배분도_없다() {
        save("2026-07-05T12:00:00+09:00", 25_000);
        goalService.create(userId, new GoalRequest("여행 자금", 1_000_000, 0));

        MonthlyReportResponse july = reportService.monthly(userId, JULY);

        assertTrue(july.finalized());
        assertEquals(25_000, july.totalSpending());
        assertNull(july.previousTotalSpending());
        assertNull(july.savedAmount());
        assertNull(july.previousRepeatCount());
        assertTrue(july.goalAllocations().isEmpty());
        assertEquals(0, goalService.list(userId).goals().getFirst().currentAmount());
        assertNull(monthlySnapshotRepository.findByUserIdAndYearMonth(userId, "2026-07").orElseThrow().getSavedAmount());
    }

    @Test
    void 이번_달은_매번_계산하고_저장하지_않는다() {
        save("2026-09-01T12:00:00+09:00", 7_000);

        MonthlyReportResponse report = reportService.monthly(userId, SEPTEMBER);

        assertFalse(report.finalized());
        assertEquals(7_000, report.totalSpending());
        assertTrue(monthlySnapshotRepository.findByUserIdAndYearMonth(userId, "2026-09").isEmpty());
    }

    @Test
    void 거래가_없는_사용자도_기본_달로_200이다() {
        MonthlyReportResponse report = reportService.monthly(userId, null);

        assertEquals("2026-09", report.yearMonth());
        assertFalse(report.finalized());
        assertEquals(0, report.totalSpending());
        assertNull(report.savedAmount());
    }

    @Test
    void 미래_달은_400이다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> reportService.monthly(userId, YearMonth.of(2026, 10)));

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    /** 같은 키(배달|NIGHT|충동|혼자) LOW 회고 3건 — 보류를 넘겨 RESOLVED · ADJUST가 되고 제안이 생긴다 (E-81). 합계 36,000. */
    private void writeThreeLowRetrospects(String yearMonth) {
        retrospectWriter.write(userId, request(save(yearMonth + "-20T23:10:00+09:00", 12_000)));
        retrospectWriter.write(userId, request(save(yearMonth + "-21T23:20:00+09:00", 15_000)));
        retrospectWriter.write(userId, request(save(yearMonth + "-22T23:30:00+09:00", 9_000)));
    }

    /** 유일한 제안을 횟수 2로 채택 — expectedSaving 24,000이 목표에 붙는다. */
    private void adoptFirstSuggestion(GoalView goal) {
        long suggestionId = suggestionService.list(userId, null).suggestions().getFirst().id();
        suggestionService.adopt(userId, suggestionId, new SuggestionAdoptRequest(2, goal.id()));
    }

    private Transaction save(String occurredAt, int amount) {
        return transactionRepository.saveAndFlush(Transaction.of(userId, OffsetDateTime.parse(occurredAt),
                "테스트배달", amount, "배달", "배달", "report-it-" + System.nanoTime()));
    }

    private static RetrospectSaveRequest request(Transaction transaction) {
        return new RetrospectSaveRequest(transaction.getId(), Satisfaction.LOW, "충동", "혼자", false, null);
    }
}
