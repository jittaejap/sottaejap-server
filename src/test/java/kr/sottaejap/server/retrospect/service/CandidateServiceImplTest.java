package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction;
import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import kr.sottaejap.server.rules.RuleParamsFixture;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회고 후보 ⓪ (E-62). D+1 컷오프·기준선·우선순위·limit 자르기를 확인한다.
 *
 * <p>고정 시각은 2026-08-24T00:00Z = KST 09:00이므로 "오늘"은 2026-08-24다. 사용자 지연은 1일이라
 * 후보 끝(열린 끝)은 2026-08-24T00:00+09:00 — 어제까지 포함하고 오늘은 뺀다.
 */
@ExtendWith(MockitoExtension.class)
class CandidateServiceImplTest {

    private static final long USER_ID = 1L;
    private static final int MONTHLY_BUDGET = 1_000_000;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RetrospectRepository retrospectRepository;
    @Mock
    private UserRepository userRepository;

    private CandidateServiceImpl candidateService;

    @BeforeEach
    void setUp() {
        candidateService = new CandidateServiceImpl(transactionRepository, retrospectRepository, userRepository,
                RuleParamsFixture.sample(),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), TimeSlot.ZONE));
    }

    @Test
    void 날짜를_안_주면_어제까지만_보고_시작일은_두지_않는다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        givenCandidates(List.of());

        candidateService.findCandidates(USER_ID, 1, null, null);

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toExclusive = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findCandidates(eq(USER_ID), from.capture(), toExclusive.capture(),
                pageable.capture());
        assertNull(from.getValue());
        assertEquals(OffsetDateTime.parse("2026-08-24T00:00+09:00"), toExclusive.getValue());
        // 규칙 미매칭 거래가 자리를 차지하지 않도록 쿼리는 limit이 아니라 상한만큼 읽는다.
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void to를_주면_그다음_날_0시를_끝으로_쓴다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        givenCandidates(List.of());

        candidateService.findCandidates(USER_ID, 1, null, LocalDate.of(2026, 8, 20));

        ArgumentCaptor<OffsetDateTime> toExclusive = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(transactionRepository).findCandidates(eq(USER_ID), any(), toExclusive.capture(), any());
        assertEquals(OffsetDateTime.parse("2026-08-21T00:00+09:00"), toExclusive.getValue());
    }

    @Test
    void from을_주면_그날_0시부터_본다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        givenCandidates(List.of());

        candidateService.findCandidates(USER_ID, 1, LocalDate.of(2026, 8, 22), null);

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toExclusive = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(transactionRepository).findCandidates(eq(USER_ID), from.capture(), toExclusive.capture(), any());
        assertEquals(OffsetDateTime.parse("2026-08-22T00:00+09:00"), from.getValue());
        assertEquals(OffsetDateTime.parse("2026-08-24T00:00+09:00"), toExclusive.getValue());
    }

    @Test
    void 예산_대비_큰_금액만_후보가_되고_미매칭_거래는_빠진다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        Transaction big = transaction(1L, "2026-08-20T12:00:00Z", "가전마트", 150_000, "쇼핑");
        Transaction small = transaction(2L, "2026-08-19T12:00:00Z", "편의점", 3_000, "쇼핑");
        givenCandidates(List.of(big, small));

        List<CandidateView> candidates = candidateService.findCandidates(USER_ID, 10, null, null);

        assertEquals(1, candidates.size());
        CandidateView view = candidates.get(0);
        assertEquals(1L, view.transactionId());
        assertEquals(ReasonCode.THRESHOLD_EXCEEDED, view.reasonCode());
        assertEquals(ReasonTemplate.reasonFor(ReasonCode.THRESHOLD_EXCEEDED), view.reason());
        assertEquals("2026-08-20T21:00+09:00", view.occurredAt().toString());
    }

    @Test
    void 같은_시간대_중앙값의_배수를_넘으면_이상치다() {
        givenUser(user(null));
        Transaction target = transaction(10L, "2026-08-20T14:00:00Z", "야식집", 25_000, "기타");
        givenBaseline(baselineOf(target));
        givenRetrospects(List.of());
        givenCandidates(List.of(target));

        List<CandidateView> candidates = candidateService.findCandidates(USER_ID, 10, null, null);

        assertEquals(1, candidates.size());
        assertEquals(ReasonCode.TIMESLOT_OUTLIER, candidates.get(0).reasonCode());
        assertEquals(ReasonTemplate.reasonFor(ReasonCode.TIMESLOT_OUTLIER), candidates.get(0).reason());
    }

    @Test
    void 사용자_임계값이_높으면_같은_거래도_이상치가_아니다() {
        givenUser(user(5.0));
        Transaction target = transaction(10L, "2026-08-20T14:00:00Z", "야식집", 25_000, "기타");
        givenBaseline(baselineOf(target));
        givenRetrospects(List.of());
        givenCandidates(List.of(target));

        assertTrue(candidateService.findCandidates(USER_ID, 10, null, null).isEmpty());
    }

    @Test
    void 같은_상위_키에_LOW_회고가_쌓이면_다시_돌아볼_후보다() {
        givenUser(user(null));
        givenBaseline(List.of());
        Transaction past1 = transaction(21L, "2026-08-10T14:00:00Z", "야식집", 12_000, "기타");
        Transaction past2 = transaction(22L, "2026-08-12T14:00:00Z", "치킨집", 18_000, "기타");
        givenRetrospects(List.of(lowRetrospect(101L, past1), lowRetrospect(102L, past2)));
        Transaction target = transaction(30L, "2026-08-20T14:00:00Z", "야식집", 9_000, "기타");
        givenCandidates(List.of(target));

        List<CandidateView> candidates = candidateService.findCandidates(USER_ID, 10, null, null);

        assertEquals(1, candidates.size());
        assertEquals(ReasonCode.REPEATED_LOW_SATISFACTION, candidates.get(0).reasonCode());
    }

    @Test
    void limit이_1이면_매칭된_것_중_최신_한_건만_준다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        Transaction newer = transaction(2L, "2026-08-21T12:00:00Z", "가전마트", 200_000, "쇼핑");
        Transaction older = transaction(1L, "2026-08-20T12:00:00Z", "가구점", 150_000, "쇼핑");
        givenCandidates(List.of(newer, older));

        List<CandidateView> candidates = candidateService.findCandidates(USER_ID, 1, null, null);

        assertEquals(1, candidates.size());
        assertEquals(2L, candidates.get(0).transactionId());
    }

    @Test
    void 어느_규칙에도_맞지_않으면_직접_선택이다() {
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        Transaction target = transaction(1L, "2026-08-20T12:00:00Z", "편의점", 3_000, "쇼핑");

        assertEquals(ReasonCode.MANUAL_PICK, candidateService.reasonCodeFor(USER_ID, target));
    }

    @Test
    void 없는_사용자면_NOT_FOUND다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> candidateService.findCandidates(USER_ID, 1, null, null));

        assertEquals(CommonErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 사용자_설정이_없는데_sensitivity_standard가_null이면_계산을_거부한다() {
        RuleParams sample = RuleParamsFixture.sample();
        CandidateServiceImpl service = new CandidateServiceImpl(transactionRepository, retrospectRepository,
                userRepository,
                new RuleParams(sample.shrinkageK(), sample.rollupMinCount(), sample.pendingMinCount(),
                        sample.axisXBoundary(), sample.axisYBoundary(), sample.chatWindowDays(),
                        new RuleParams.Sensitivity(3.0, null, 1.5), sample.candidate(), sample.cluster()),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), TimeSlot.ZONE));
        givenUser(user(null));
        givenBaseline(List.of());
        givenRetrospects(List.of());
        Transaction target = transaction(1L, "2026-08-20T12:00:00Z", "편의점", 3_000, "쇼핑");

        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> service.reasonCodeFor(USER_ID, target));

        assertTrue(exception.getMessage().contains("rules.sensitivity.standard"));
    }

    /** 대상과 같은 (기타, NIGHT) 거래 5건 + 대상 자신. 자신을 빼야 표본이 정확히 outlier-min-samples가 된다. */
    private static List<Transaction> baselineOf(Transaction target) {
        return List.of(
                transaction(1L, "2026-08-14T14:00:00Z", "야식집", 10_000, "기타"),
                transaction(2L, "2026-08-15T14:00:00Z", "야식집", 10_000, "기타"),
                transaction(3L, "2026-08-16T14:00:00Z", "야식집", 10_000, "기타"),
                transaction(4L, "2026-08-17T14:00:00Z", "야식집", 10_000, "기타"),
                transaction(5L, "2026-08-18T14:00:00Z", "야식집", 10_000, "기타"),
                target);
    }

    private static Transaction transaction(long id, String occurredAt, String merchant, int amount, String category) {
        Transaction transaction = Transaction.of(USER_ID, OffsetDateTime.parse(occurredAt), merchant, amount,
                category, null, "hash-" + id);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static RetrospectWithTransaction lowRetrospect(long id, Transaction transaction) {
        Retrospect retrospect = Retrospect.completed(transaction.getId(), Satisfaction.LOW, null, null, null,
                RetrospectSource.CANDIDATE, transaction.getOccurredAt());
        ReflectionTestUtils.setField(retrospect, "id", id);
        return new RetrospectWithTransaction(retrospect, transaction);
    }

    private static User user(Double outlierThreshold) {
        User user = User.social(AuthProvider.KAKAO, "kakao-1", "선생님", "user@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "monthlyBudget", MONTHLY_BUDGET);
        ReflectionTestUtils.setField(user, "outlierThreshold", outlierThreshold);
        return user;
    }

    private void givenUser(User user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenBaseline(List<Transaction> transactions) {
        when(transactionRepository.findAllByUserIdAndOccurredAtGreaterThanEqual(eq(USER_ID), any()))
                .thenReturn(transactions);
    }

    private void givenRetrospects(List<RetrospectWithTransaction> rows) {
        when(retrospectRepository.findAllWithTransactionByUserId(USER_ID)).thenReturn(rows);
    }

    private void givenCandidates(List<Transaction> transactions) {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions);
    }
}
