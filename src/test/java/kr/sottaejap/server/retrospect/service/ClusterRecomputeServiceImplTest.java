package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction;
import kr.sottaejap.server.rules.RuleParamsFixture;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 사용자 전체 재계산 (E-61). 상위 묶음을 먼저 저장해 리프의 parentId를 채우고(E-59),
 * 직접 구성원 거래에 behaviorId를 배정하며, 사용자 평균 캐시를 갱신한다.
 */
@ExtendWith(MockitoExtension.class)
class ClusterRecomputeServiceImplTest {

    private static final long USER_ID = 7L;
    /** KST 2026-08-24 22:30 — NIGHT(22~05) 구간이라 묶음 키에 시간대가 들어간다. */
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-24T13:30:00Z");

    @Mock
    private RetrospectRepository retrospectRepository;
    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;

    private ClusterRecomputeServiceImpl service;
    private User user;
    private final Map<Long, Transaction> transactionsById = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        service = new ClusterRecomputeServiceImpl(retrospectRepository, behaviorClusterRepository,
                transactionRepository, userRepository, RuleParamsFixture.sample());
        user = User.social(AuthProvider.KAKAO, "kakao-1", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "monthlyBudget", 1_000_000);
        transactionsById.clear();
    }

    @Test
    void 같은_키_회고_3건은_묶음_하나를_만들고_거래_3건에_behaviorId를_배정한다() {
        givenRetrospects(
                retrospected(1L, "배달의민족", 12000, Satisfaction.LOW),
                retrospected(2L, "쿠팡이츠", 15000, Satisfaction.LOW),
                retrospected(3L, "배달의민족", 9000, Satisfaction.HIGH));
        givenNoExistingCluster();
        givenSaveAssignsIds();
        givenTransactionLookup();

        List<BehaviorCluster> saved = service.recomputeAll(USER_ID);

        assertEquals(1, saved.size());
        BehaviorCluster leaf = saved.get(0);
        assertEquals("배달|NIGHT|충동|혼자", leaf.getClusterKey());
        assertEquals(EvaluationStatus.RESOLVED, leaf.getEvaluationStatus());
        assertNull(leaf.getParentId());
        assertEquals(3, leaf.getRetrospectCount());
        assertEquals(List.of(leaf.getId(), leaf.getId(), leaf.getId()),
                transactionsById.values().stream().map(Transaction::getBehaviorId).toList());
        assertEquals(-1.0 / 3, user.getAvgSatisfaction(), 1e-9);
    }

    @Test
    void 회고_2건은_상위_묶음을_먼저_저장하고_리프에_그_id를_건다() {
        givenRetrospects(
                retrospected(1L, "배달의민족", 12000, Satisfaction.LOW),
                retrospected(2L, "쿠팡이츠", 15000, Satisfaction.LOW));
        givenNoExistingCluster();
        givenSaveAssignsIds();
        givenTransactionLookup();

        List<BehaviorCluster> saved = service.recomputeAll(USER_ID);

        assertEquals(2, saved.size());
        BehaviorCluster parent = saved.get(0);
        BehaviorCluster leaf = saved.get(1);
        assertEquals("배달|NIGHT||", parent.getClusterKey());
        assertEquals("배달|NIGHT|충동|혼자", leaf.getClusterKey());
        assertNull(parent.getParentId());
        assertEquals(parent.getId(), leaf.getParentId());
        assertEquals(EvaluationStatus.PENDING, leaf.getEvaluationStatus());
        // 상위 묶음은 직접 구성원이 없다 — behaviorId는 언제나 리프다 (E-59).
        assertTrue(transactionsById.values().stream().allMatch(tx -> leaf.getId().equals(tx.getBehaviorId())));
    }

    @Test
    void 이미_있는_키는_새로_만들지_않고_그_행을_갱신한다() {
        givenRetrospects(
                retrospected(1L, "배달의민족", 12000, Satisfaction.LOW),
                retrospected(2L, "쿠팡이츠", 15000, Satisfaction.LOW),
                retrospected(3L, "배달의민족", 9000, Satisfaction.HIGH));
        BehaviorCluster existing = BehaviorCluster.create(USER_ID, "배달|NIGHT|충동|혼자");
        ReflectionTestUtils.setField(existing, "id", 41L);
        existing.rename("심야 배달");
        when(behaviorClusterRepository.findByUserIdAndClusterKey(USER_ID, "배달|NIGHT|충동|혼자"))
                .thenReturn(Optional.of(existing));
        givenSaveAssignsIds();
        givenTransactionLookup();

        List<BehaviorCluster> saved = service.recomputeAll(USER_ID);

        assertEquals(1, saved.size());
        assertEquals(41L, saved.get(0).getId().longValue());
        // 이름은 재계산이 건드리지 않는다 (E-64).
        assertEquals("심야 배달", saved.get(0).getDisplayName());
        assertEquals(EvaluationStatus.RESOLVED, saved.get(0).getEvaluationStatus());
    }

    @Test
    void 이번_결과에_없는_기존_묶음은_지우지_않고_값을_비운다() {
        givenRetrospects(
                retrospected(1L, "배달의민족", 12000, Satisfaction.LOW),
                retrospected(2L, "쿠팡이츠", 15000, Satisfaction.LOW),
                retrospected(3L, "요기요", 9000, Satisfaction.HIGH));
        givenNoExistingCluster();
        givenSaveAssignsIds();
        givenTransactionLookup();
        BehaviorCluster stale = BehaviorCluster.create(USER_ID, "배달|NIGHT||");
        ReflectionTestUtils.setField(stale, "id", 50L);
        ReflectionTestUtils.setField(stale, "retrospectCount", 2);
        ReflectionTestUtils.setField(stale, "verdict", Verdict.ADJUST);
        when(behaviorClusterRepository.findAllByUserIdOrderByClusterKeyAsc(USER_ID)).thenReturn(List.of(stale));

        service.recomputeAll(USER_ID);

        assertTrue(stale.isEmpty());
        assertNull(stale.getVerdict());
        assertEquals(EvaluationStatus.PENDING, stale.getEvaluationStatus());
        verify(behaviorClusterRepository, never()).delete(any());
    }

    @Test
    void 거래가_없으면_아무것도_저장하지_않고_빈_목록을_준다() {
        when(transactionRepository.findTopByUserIdOrderByOccurredAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertEquals(List.of(), service.recomputeAll(USER_ID));

        verifyNoInteractions(behaviorClusterRepository, userRepository);
        verify(retrospectRepository, never()).findAllWithTransactionByUserId(anyLong());
    }

    private void givenRetrospects(RetrospectWithTransaction... rows) {
        List<RetrospectWithTransaction> list = List.of(rows);
        when(transactionRepository.findTopByUserIdOrderByOccurredAtDesc(USER_ID))
                .thenReturn(Optional.of(list.get(0).transaction()));
        when(retrospectRepository.findAllWithTransactionByUserId(USER_ID)).thenReturn(list);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenNoExistingCluster() {
        when(behaviorClusterRepository.findByUserIdAndClusterKey(anyLong(), anyString())).thenReturn(Optional.empty());
    }

    /** JPA가 채워 주는 id를 흉내낸다 — 상위 묶음 id가 있어야 리프의 parentId를 검사할 수 있다. */
    private void givenSaveAssignsIds() {
        AtomicLong sequence = new AtomicLong(100L);
        when(behaviorClusterRepository.save(any(BehaviorCluster.class))).thenAnswer(invocation -> {
            BehaviorCluster cluster = invocation.getArgument(0);
            if (cluster.getId() == null) {
                ReflectionTestUtils.setField(cluster, "id", sequence.incrementAndGet());
            }
            return cluster;
        });
    }

    private void givenTransactionLookup() {
        when(transactionRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            List<Transaction> found = new ArrayList<>();
            ids.forEach(id -> found.add(transactionsById.get(id)));
            return found;
        });
    }

    private RetrospectWithTransaction retrospected(long transactionId, String merchant, int amount,
                                                   Satisfaction satisfaction) {
        Transaction transaction = Transaction.of(USER_ID, OCCURRED_AT, merchant, amount, "배달", "배달", "hash-" + transactionId);
        ReflectionTestUtils.setField(transaction, "id", transactionId);
        transactionsById.put(transactionId, transaction);
        Retrospect retrospect = Retrospect.completed(transactionId, satisfaction, "충동", "혼자", null,
                RetrospectSource.CANDIDATE, OCCURRED_AT);
        return new RetrospectWithTransaction(retrospect, transaction);
    }
}
