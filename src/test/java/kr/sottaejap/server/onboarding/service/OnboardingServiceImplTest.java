package kr.sottaejap.server.onboarding.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 온보딩 표본 추출과 완료 (E-92 · FR-09-01,02).
 *
 * <p>기간은 26.06.01~26.07.31이고 거래는 그 안에서 최신순으로 온다고 본다 — 기간 조건과 미회고 조건은
 * 쿼리(`findCandidates`)가 이미 거른다. 여기서 확인하는 것은 그 목록에서 무엇을 어떻게 고르는가다.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    private static final long USER_ID = 1L;
    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate PERIOD_TO = LocalDate.of(2026, 7, 31);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClusterRecomputeService clusterRecomputeService;
    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;

    private OnboardingServiceImpl onboardingService;

    @BeforeEach
    void setUp() {
        onboardingService = new OnboardingServiceImpl(
                transactionRepository, userRepository, clusterRecomputeService, behaviorClusterRepository);
    }

    @Test
    void 표본은_기간_전체에_같은_간격으로_펼친다() {
        // 10건에서 5건 → 간격 2 → 0·2·4·6·8번째. 앞에서 5건을 자르면 마지막 주만 남는다
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions(10));

        CandidateListResponse response = onboardingService.start(USER_ID, request(5));

        assertEquals(List.of(1L, 3L, 5L, 7L, 9L),
                response.candidates().stream().map(candidate -> candidate.transactionId()).toList());
    }

    @Test
    void 표본의_선정_사유는_전부_ONBOARDING_SAMPLE이다() {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions(3));

        CandidateListResponse response = onboardingService.start(USER_ID, request(3));

        assertTrue(response.candidates().stream()
                .allMatch(candidate -> candidate.reasonCode() == ReasonCode.ONBOARDING_SAMPLE));
        assertEquals("최근 소비 중에서 함께 돌아볼 거래로 골랐어요.", response.candidates().getFirst().reason());
    }

    @Test
    void 거래가_표본보다_적으면_있는_만큼만_준다() {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions(3));

        assertEquals(3, onboardingService.start(USER_ID, request(20)).candidates().size());
    }

    @Test
    void 회고할_거래가_없으면_빈_목록이다() {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(List.of());

        assertTrue(onboardingService.start(USER_ID, request(20)).candidates().isEmpty());
    }

    @Test
    void sampleSize가_100을_넘으면_400이_아니라_100으로_자른다() {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions(150));

        assertEquals(100, onboardingService.start(USER_ID, request(200)).candidates().size());
    }

    @Test
    void 기간_경계는_KST_자정이고_끝은_열려_있다() {
        when(transactionRepository.findCandidates(eq(USER_ID), any(), any(), any())).thenReturn(transactions(1));

        onboardingService.start(USER_ID, request(20));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toExclusive = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(transactionRepository)
                .findCandidates(eq(USER_ID), from.capture(), toExclusive.capture(), eq(Pageable.unpaged()));
        assertEquals(OffsetDateTime.parse("2026-06-01T00:00+09:00"), from.getValue());
        assertEquals(OffsetDateTime.parse("2026-08-01T00:00+09:00"), toExclusive.getValue());
    }

    @Test
    void 시작일이_종료일보다_뒤면_400이다() {
        BusinessException exception = assertThrows(BusinessException.class, () -> onboardingService.start(
                USER_ID, new OnboardingStartRequest(20, PERIOD_TO, PERIOD_FROM)));

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verify(transactionRepository, never()).findCandidates(eq(USER_ID), any(), any(), any());
    }

    @Test
    void 완료는_플래그를_세우고_지도를_다시_계산한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID))
                .thenReturn(Collections.nCopies(15, mock(BehaviorCluster.class)));

        OnboardingCompleteResponse response = onboardingService.complete(USER_ID);

        assertTrue(user.isOnboardingCompleted());
        assertTrue(response.onboardingCompleted());
        assertEquals(15, response.clusterCount());
        verify(clusterRecomputeService).recomputeAll(USER_ID);
    }

    @Test
    void 회고가_한_건도_없으면_묶음_0개로_완료한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID)).thenReturn(List.of());

        assertEquals(0, onboardingService.complete(USER_ID).clusterCount());
        assertTrue(user.isOnboardingCompleted());
    }

    @Test
    void 없는_사용자는_404다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertEquals(CommonErrorCode.NOT_FOUND,
                assertThrows(BusinessException.class, () -> onboardingService.complete(USER_ID)).getErrorCode());
        verify(clusterRecomputeService, never()).recomputeAll(USER_ID);
    }

    private static OnboardingStartRequest request(int sampleSize) {
        return new OnboardingStartRequest(sampleSize, PERIOD_FROM, PERIOD_TO);
    }

    /** 최신순 목록을 흉내 낸다 — id 1번이 가장 최신이다. */
    private static List<Transaction> transactions(int count) {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Transaction transaction = Transaction.of(USER_ID, OffsetDateTime.parse("2026-07-31T12:00+09:00"),
                    "가맹점" + i, 10_000, "식사", null, "hash-" + i);
            ReflectionTestUtils.setField(transaction, "id", (long) i);
            transactions.add(transaction);
        }
        return transactions;
    }

    private static User user() {
        User user = User.social(AuthProvider.LOCAL, "demo", "데모 사용자", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        assertFalse(user.isOnboardingCompleted());
        return user;
    }
}
