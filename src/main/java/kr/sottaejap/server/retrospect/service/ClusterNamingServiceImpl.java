package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 묶음 이름(⑤)을 AI에게 받아 채운다 (E-64).
 *
 * <p>저장 트랜잭션이 <b>커밋된 뒤</b> 부르고, 이 메서드 자체는 트랜잭션 밖에서 돈다 — AI 왕복(최대 15초) 동안
 * DB 트랜잭션을 열어 두지 않는다. 이름 확정만 {@link TransactionTemplate}으로 짧게 감싼다.
 * displayName이 이미 있는 묶음은 다시 짓지 않는다.
 */
@Service
public class ClusterNamingServiceImpl implements ClusterNamingService {

    /** AI는 task_context.state로 판단한다 — message는 작업을 알리는 한 문장이면 된다 (05 §3). */
    private static final String NAMING_MESSAGE = "묶음 이름을 지어 주세요";

    /** 05 §3 sample_merchants — 가맹점 표본 최대 3개 (규칙 값이 아니라 프롬프트 크기). */
    private static final int SAMPLE_MERCHANT_LIMIT = 3;

    private final BehaviorClusterRepository behaviorClusterRepository;
    private final TransactionRepository transactionRepository;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    public ClusterNamingServiceImpl(BehaviorClusterRepository behaviorClusterRepository,
                                    TransactionRepository transactionRepository, AiClient aiClient,
                                    PlatformTransactionManager transactionManager) {
        this.behaviorClusterRepository = behaviorClusterRepository;
        this.transactionRepository = transactionRepository;
        this.aiClient = aiClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void nameUnnamed(long userId) {
        List<BehaviorCluster> unnamed =
                behaviorClusterRepository.findAllByUserIdAndDisplayNameIsNullOrderByClusterKeyAsc(userId);
        for (BehaviorCluster cluster : unnamed) {
            if (cluster.hasDisplayName()) {
                continue;
            }
            String name = resolveName(userId, cluster);
            // AI 왕복 동안 재계산이 같은 행을 바꿨을 수 있다 — 떼어진 엔티티를 merge하지 않고 다시 읽어 이름만 바꾼다
            transactionTemplate.executeWithoutResult(status ->
                    behaviorClusterRepository.findById(cluster.getId())
                            .filter(fresh -> !fresh.hasDisplayName())
                            .ifPresent(fresh -> fresh.rename(name)));
        }
    }

    /** AI가 없거나(503) 빈 문장을 주면 템플릿으로 짓는다 (E-38 · E-64). fallback 응답도 문장이므로 그대로 쓴다. */
    private String resolveName(long userId, BehaviorCluster cluster) {
        try {
            ChatResponse response = aiClient.chat(new ChatRequest(
                    NAMING_MESSAGE,
                    String.valueOf(userId),
                    new TaskContext(TaskType.CLUSTER_NAMING, RetrospectStatus.ACTIVE, state(cluster)),
                    List.of()));
            String reply = response.reply() == null ? "" : response.reply().strip();
            if (reply.isBlank()) {
                return ClusterNameTemplate.nameFor(cluster.getClusterKey());
            }
            return ClusterNameTemplate.truncate(reply);
        } catch (BusinessException llmUnavailable) {
            return ClusterNameTemplate.nameFor(cluster.getClusterKey());
        }
    }

    /**
     * 05 §3 CLUSTER_NAMING state — `cluster_key` · `sample_merchants` · `tx_count`.
     * 표본은 이 묶음에 배정된 거래(리프의 직접 구성원)의 가맹점을 중복 없이 최대 3개. 상위 묶음은 직접 구성원이
     * 없을 수 있어 빈 표본이 갈 수 있다 — AI는 빈 표본도 받는다.
     */
    private Map<String, Object> state(BehaviorCluster cluster) {
        List<Transaction> members = transactionRepository.findAllByBehaviorIdOrderByOccurredAtDesc(cluster.getId());
        List<String> merchants = members.stream().map(Transaction::getMerchant).distinct()
                .limit(SAMPLE_MERCHANT_LIMIT).toList();
        return Map.of(
                "cluster_key", cluster.getClusterKey(),
                "sample_merchants", merchants,
                "tx_count", members.isEmpty() ? cluster.getRetrospectCount() : members.size());
    }
}
