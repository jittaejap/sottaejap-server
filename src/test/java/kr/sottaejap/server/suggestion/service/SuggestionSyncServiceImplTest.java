package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 제안 동기화 (E-81) — 재계산 결과에 맞춰 PROPOSED만 만들고 지운다. */
@ExtendWith(MockitoExtension.class)
class SuggestionSyncServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private SuggestionRepository suggestionRepository;

    @InjectMocks
    private SuggestionSyncServiceImpl service;

    @Test
    void 새_대상에는_제안을_만들고_횟수는_거래_건수다() {
        given(List.of(cluster(1L, Quadrant.MINOR, Verdict.ADJUST)), List.of());

        service.sync(USER_ID);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getBehaviorId());
        assertEquals(8, captor.getValue().getAdjustCount());
        assertEquals(96_000, captor.getValue().getExpectedSaving());
        assertEquals(SuggestionStatus.PROPOSED, captor.getValue().getStatus());
    }

    @Test
    void 이미_있는_제안은_새로_만들지_않고_제자리에서_갱신한다() {
        Suggestion existing = Suggestion.propose(1L, 3, 36_000);
        given(List.of(cluster(1L, Quadrant.MINOR, Verdict.ADJUST)), List.of(existing));

        service.sync(USER_ID);

        verify(suggestionRepository, never()).save(any());
        assertEquals(8, existing.getAdjustCount());
        assertEquals(96_000, existing.getExpectedSaving());
    }

    @Test
    void 대상에서_빠진_제안은_지우고_곧바로_flush한다() {
        Suggestion stale = Suggestion.propose(9L, 3, 36_000);
        given(List.of(cluster(1L, Quadrant.MINOR, Verdict.ADJUST)), List.of(stale));

        service.sync(USER_ID);

        InOrder order = inOrder(suggestionRepository);
        order.verify(suggestionRepository).deleteAll(List.of(stale));
        // 부분 유일 인덱스 때문에 INSERT보다 DELETE가 먼저 나가야 한다
        order.verify(suggestionRepository).flush();
        order.verify(suggestionRepository).save(any());
    }

    @Test
    void 채택하거나_거절한_제안은_보존하고_그_묶음에_새_제안을_만들지_않는다() {
        Suggestion adopted = Suggestion.propose(1L, 3, 36_000);
        adopted.adopt(2, 24_000, 5L);
        Suggestion rejected = Suggestion.propose(2L, 3, 36_000);
        rejected.reject();
        given(List.of(cluster(1L, Quadrant.MINOR, Verdict.ADJUST), cluster(2L, Quadrant.PRIORITY, Verdict.ADJUST)),
                List.of(adopted, rejected));

        service.sync(USER_ID);

        verify(suggestionRepository, never()).save(any());
        verify(suggestionRepository, never()).deleteAll(any());
        assertEquals(24_000, adopted.getExpectedSaving());
        assertEquals(SuggestionStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void 지킬_소비와_보류_묶음은_대상이_아니다() {
        given(List.of(cluster(1L, Quadrant.PROTECT, Verdict.SUSTAIN), pendingCluster(2L)), List.of());

        service.sync(USER_ID);

        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void 대상이_하나도_없으면_지울_것도_없으면_아무_일도_하지_않는다() {
        given(List.of(), List.of());

        service.sync(USER_ID);

        verify(suggestionRepository, never()).save(any());
        verify(suggestionRepository, never()).deleteAll(any());
        verify(suggestionRepository, never()).flush();
    }

    private void given(List<BehaviorCluster> clusters, List<Suggestion> existing) {
        when(behaviorClusterRepository.findEffectiveByUserId(USER_ID)).thenReturn(clusters);
        when(suggestionRepository.findAllByUserId(anyLong())).thenReturn(existing);
    }

    private static BehaviorCluster cluster(long id, Quadrant quadrant, Verdict verdict) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, "배달|NIGHT||" + id);
        cluster.apply(new ClusterEvaluation("배달|NIGHT||" + id, null, 4, -0.5, -0.42, 12_000, 96_000,
                YearMonth.of(2026, 8), 8, 0.096, EvaluationStatus.RESOLVED, quadrant, verdict,
                List.of(), List.of()), null);
        ReflectionTestUtils.setField(cluster, "id", id);
        return cluster;
    }

    private static BehaviorCluster pendingCluster(long id) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, "카페|DAY||" + id);
        cluster.apply(new ClusterEvaluation("카페|DAY||" + id, null, 2, -0.5, -0.42, 12_000, 24_000,
                YearMonth.of(2026, 8), 2, 0.024, EvaluationStatus.PENDING, null, null,
                List.of(), List.of()), null);
        ReflectionTestUtils.setField(cluster, "id", id);
        return cluster;
    }
}
