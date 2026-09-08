package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.BehaviorListResponse;
import kr.sottaejap.server.analysis.dto.BehaviorView;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 묶음 목록 · 상세 (05 §2 · FR-07-05). */
@ExtendWith(MockitoExtension.class)
class BehaviorServiceImplTest {

    private static final long USER_ID = 7L;
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-24T13:30:00Z");

    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BehaviorServiceImpl service;

    @Test
    void 목록은_바꿔볼_소비부터_보여_준다() {
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID)).thenReturn(List.of(
                cluster(1L, "카페|DAY||", null, 168_000, 6, 0.168, Quadrant.PROTECT, Verdict.SUSTAIN, "낮 카페"),
                cluster(2L, "배달|NIGHT||", null, 96_000, 8, 0.096, Quadrant.MINOR, Verdict.ADJUST, "심야 배달")));

        BehaviorListResponse response = service.behaviors(USER_ID);

        assertEquals(List.of(2L, 1L), response.behaviors().stream().map(BehaviorView::behaviorId).toList());
    }

    @Test
    void 이름이_아직_없으면_묶음_키로_만든_이름을_쓴다() {
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID)).thenReturn(List.of(
                cluster(1L, "배달|NIGHT||", null, 96_000, 8, 0.096, Quadrant.MINOR, Verdict.ADJUST, null)));

        assertEquals("심야 배달", service.behaviors(USER_ID).behaviors().getFirst().name());
    }

    @Test
    void 상세는_롤업된_리프도_열어_주고_parentId를_알려_준다() {
        BehaviorCluster leaf = cluster(5L, "배달|NIGHT|충동|혼자", 1L, 96_000, 8, 0.096,
                Quadrant.MINOR, Verdict.ADJUST, "심야 배달");
        when(behaviorClusterRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(leaf));
        when(behaviorClusterRepository.findAllByParentId(5L)).thenReturn(List.of());
        when(transactionRepository.findAllByBehaviorIdInOrderByOccurredAtDescIdDesc(anyList())).thenReturn(List.of());

        BehaviorDetailResponse response = service.behavior(USER_ID, 5L);

        assertEquals(1L, response.behavior().parentId());
    }

    @Test
    void 상세의_거래는_자식_묶음_것까지_합친다() {
        BehaviorCluster parent = cluster(1L, "배달|NIGHT||", null, 96_000, 8, 0.096,
                Quadrant.MINOR, Verdict.ADJUST, "심야 배달");
        BehaviorCluster child = cluster(5L, "배달|NIGHT|충동|혼자", 1L, 96_000, 8, 0.096,
                Quadrant.MINOR, Verdict.ADJUST, "심야 배달");
        when(behaviorClusterRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(parent));
        when(behaviorClusterRepository.findAllByParentId(1L)).thenReturn(List.of(child));
        when(transactionRepository.findAllByBehaviorIdInOrderByOccurredAtDescIdDesc(anyList()))
                .thenReturn(List.of(transaction(11L)));

        BehaviorDetailResponse response = service.behavior(USER_ID, 1L);

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.captor();
        verify(transactionRepository).findAllByBehaviorIdInOrderByOccurredAtDescIdDesc(captor.capture());
        assertEquals(List.of(1L, 5L), List.copyOf(captor.getValue()));
        assertEquals(1, response.transactions().size());
    }

    @Test
    void 회고가_0인_묶음과_남의_묶음은_구별하지_않고_404다() {
        BehaviorCluster emptied = cluster(1L, "배달|NIGHT||", null, 96_000, 8, 0.096,
                Quadrant.MINOR, Verdict.ADJUST, "심야 배달");
        emptied.markEmpty();
        when(behaviorClusterRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(emptied));
        when(behaviorClusterRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.behavior(USER_ID, 1L));
        assertThrows(BusinessException.class, () -> service.behavior(USER_ID, 9L));
    }

    private static BehaviorCluster cluster(long id, String clusterKey, Long parentId, int monthlyTotalAmount,
                                           int txCount, Double burdenRatio, Quadrant quadrant, Verdict verdict,
                                           String displayName) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, clusterKey);
        cluster.apply(new ClusterEvaluation(clusterKey, null, 4, -0.5, -0.42,
                monthlyTotalAmount / txCount, monthlyTotalAmount, YearMonth.of(2026, 8), txCount, burdenRatio,
                EvaluationStatus.RESOLVED, quadrant, verdict, List.of(), List.of()), parentId);
        ReflectionTestUtils.setField(cluster, "id", id);
        if (displayName != null) {
            cluster.rename(displayName);
        }
        return cluster;
    }

    private static Transaction transaction(long id) {
        Transaction transaction = Transaction.of(USER_ID, OCCURRED_AT, "배달의민족", 12_000, "배달", "배달", "hash-" + id);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }
}
