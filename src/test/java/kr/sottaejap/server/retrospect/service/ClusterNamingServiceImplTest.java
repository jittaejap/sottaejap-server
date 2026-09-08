package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 묶음 이름(⑤ · E-64). AI 왕복은 트랜잭션 밖이고, 503·빈 응답이면 템플릿으로 대체한다 (E-38).
 */
@ExtendWith(MockitoExtension.class)
class ClusterNamingServiceImplTest {

    private static final long USER_ID = 7L;
    private static final String CLUSTER_KEY = "배달|NIGHT|충동|혼자";
    /** 저장 응답에 실리는 리프 — AI로 이름을 짓는 유일한 묶음이다. */
    private static final long LEAF_ID = 100L;

    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AiClient aiClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ClusterNamingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClusterNamingServiceImpl(behaviorClusterRepository, transactionRepository, aiClient, transactionManager);
    }

    @Test
    void 이름이_이미_있는_묶음은_AI를_부르지_않는다() {
        BehaviorCluster named = cluster(CLUSTER_KEY);
        named.rename("심야 배달");
        givenUnnamed(named);

        service.nameUnnamed(USER_ID, LEAF_ID);

        verifyNoInteractions(aiClient);
        verify(behaviorClusterRepository, never()).findById(any());
    }

    @Test
    void AI가_준_이름을_그대로_붙인다() {
        BehaviorCluster cluster = cluster(CLUSTER_KEY);
        givenUnnamed(cluster);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("야식 배달"));

        service.nameUnnamed(USER_ID, LEAF_ID);

        assertEquals("야식 배달", cluster.getDisplayName());
        verify(behaviorClusterRepository).findById(LEAF_ID);
    }

    @Test
    void AI가_503이면_템플릿_이름을_쓴다() {
        BehaviorCluster cluster = cluster(CLUSTER_KEY);
        givenUnnamed(cluster);
        when(aiClient.chat(any(ChatRequest.class)))
                .thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        service.nameUnnamed(USER_ID, LEAF_ID);

        assertEquals("심야 배달", cluster.getDisplayName());
    }

    @Test
    void 빈_응답이면_템플릿_이름을_쓴다() {
        BehaviorCluster cluster = cluster("교통||필수품|혼자");
        givenUnnamed(cluster);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("   "));

        service.nameUnnamed(USER_ID, LEAF_ID);

        assertEquals("교통 필수품", cluster.getDisplayName());
    }

    @Test
    void AI_이름이_열두_자를_넘으면_열두_자로_자른다() {
        BehaviorCluster cluster = cluster(CLUSTER_KEY);
        givenUnnamed(cluster);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("가나다라마바사아자차카타파"));

        service.nameUnnamed(USER_ID, LEAF_ID);

        assertEquals("가나다라마바사아자차카타", cluster.getDisplayName());
    }

    @Test
    void CLUSTER_NAMING_요청은_문서_05의_state_구조를_보낸다() {
        BehaviorCluster cluster = cluster(CLUSTER_KEY);
        ReflectionTestUtils.setField(cluster, "retrospectCount", 3);
        givenUnnamed(cluster);
        when(transactionRepository.findAllByBehaviorIdOrderByOccurredAtDesc(LEAF_ID)).thenReturn(List.of(
                tx("배달의민족"), tx("쿠팡이츠"), tx("배달의민족")));
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("심야 배달"));

        service.nameUnnamed(USER_ID, LEAF_ID);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals(TaskType.CLUSTER_NAMING, request.taskContext().task());
        assertEquals(RetrospectStatus.ACTIVE, request.taskContext().status());
        assertEquals("7", request.userId());
        assertEquals(List.of(), request.recentMessages());
        Map<String, Object> state = request.taskContext().state();
        assertEquals(CLUSTER_KEY, state.get("cluster_key"));
        assertEquals(List.of("배달의민족", "쿠팡이츠"), state.get("sample_merchants"));
        assertEquals(3, state.get("tx_count"));
        assertTrue(state.keySet().containsAll(List.of("cluster_key", "sample_merchants", "tx_count")));
    }

    @Test
    void 리프가_아닌_묶음은_AI를_부르지_않고_템플릿으로_짓는다() {
        BehaviorCluster leaf = cluster(CLUSTER_KEY);
        BehaviorCluster rollup = cluster("배달|NIGHT||");
        ReflectionTestUtils.setField(rollup, "id", 200L);
        when(behaviorClusterRepository.findAllByUserIdAndDisplayNameIsNullOrderByClusterKeyAsc(USER_ID))
                .thenReturn(List.of(rollup, leaf));
        when(behaviorClusterRepository.findById(LEAF_ID)).thenReturn(Optional.of(leaf));
        when(behaviorClusterRepository.findById(200L)).thenReturn(Optional.of(rollup));
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("야식 배달"));

        service.nameUnnamed(USER_ID, LEAF_ID);

        assertEquals("야식 배달", leaf.getDisplayName());
        assertEquals("심야 배달", rollup.getDisplayName());
        verify(aiClient, times(1)).chat(any(ChatRequest.class));
    }

    /** 이름 확정은 AI 왕복 뒤 다시 읽은 엔티티에 한다 — 같은 객체를 돌려주는 것으로 흉내 낸다. */
    private void givenUnnamed(BehaviorCluster cluster) {
        when(behaviorClusterRepository.findAllByUserIdAndDisplayNameIsNullOrderByClusterKeyAsc(USER_ID))
                .thenReturn(List.of(cluster));
        lenient().when(behaviorClusterRepository.findById(cluster.getId())).thenReturn(Optional.of(cluster));
    }

    private static BehaviorCluster cluster(String clusterKey) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, clusterKey);
        ReflectionTestUtils.setField(cluster, "id", LEAF_ID);
        return cluster;
    }

    @Test
    void 배정된_거래가_없으면_표본은_비고_tx_count는_회고_수다() {
        BehaviorCluster cluster = cluster(CLUSTER_KEY);
        ReflectionTestUtils.setField(cluster, "retrospectCount", 2);
        givenUnnamed(cluster);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("심야 배달"));

        service.nameUnnamed(USER_ID, LEAF_ID);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertEquals(List.of(), captor.getValue().taskContext().state().get("sample_merchants"));
        assertEquals(2, captor.getValue().taskContext().state().get("tx_count"));
    }

    private static Transaction tx(String merchant) {
        return Transaction.of(USER_ID, OffsetDateTime.parse("2026-08-22T23:10:00+09:00"), merchant, 12000, "배달", null, merchant);
    }

    private static ChatResponse reply(String reply) {
        return new ChatResponse(reply, List.of(), false, false);
    }
}
