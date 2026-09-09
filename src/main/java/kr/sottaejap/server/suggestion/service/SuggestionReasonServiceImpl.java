package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 제안 이유 문장을 AI에게 받아 채운다 (05 §3 ACTION_PLAN · FR-08-01).
 *
 * <p>{@link kr.sottaejap.server.retrospect.service.ClusterNamingServiceImpl}과 같은 자리·같은 방식이다 —
 * 저장 트랜잭션이 <b>커밋된 뒤</b> 부르고 이 메서드 자체는 트랜잭션 밖에서 돈다. AI 왕복(최대 15초) 동안 DB
 * 트랜잭션을 열어 두지 않고, 문장 저장만 {@link TransactionTemplate}으로 짧게 감싼다 (E-64).
 *
 * <p>커밋 뒤여야 하는 이유가 하나 더 있다 — <b>AI가 Spring을 되부른다.</b> Handler는
 * {@code /internal/ai/users/{userId}/suggestions}로 제안 상세를 당겨 가므로, 커밋 전에 부르면 그 조회에
 * 방금 만든 제안이 보이지 않는다.
 *
 * <p><b>AI 왕복은 저장 1건당 한 번뿐이다</b> — 방금 회고한 리프 묶음의 제안 하나만 짓는다. 제안은 묶음이
 * 조정 대상이 되는 순간 생기고 그 순간이 곧 그 묶음에 회고를 저장한 순간이라, 새로 생긴 제안은 대개 이 하나다.
 */
@Service
public class SuggestionReasonServiceImpl implements SuggestionReasonService {

    /** AI는 task_context.state로 판단한다 — message는 작업을 알리는 한 문장이면 된다 (05 §3). */
    private static final String REASON_MESSAGE = "이 제안을 하게 된 이유를 설명해 주세요";

    private final SuggestionRepository suggestionRepository;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    public SuggestionReasonServiceImpl(SuggestionRepository suggestionRepository, AiClient aiClient,
                                       PlatformTransactionManager transactionManager) {
        this.suggestionRepository = suggestionRepository;
        this.aiClient = aiClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void explainProposed(long userId, long behaviorId) {
        Optional<Suggestion> target = transactionTemplate.execute(status ->
                suggestionRepository.findProposedByUserIdAndBehaviorId(userId, behaviorId)
                        .filter(suggestion -> suggestion.getReason() == null));
        if (target == null || target.isEmpty()) {
            return;
        }

        String reason = resolveReason(userId, target.get().getId());
        if (reason.isBlank()) {
            return;
        }
        // AI 왕복 동안 재계산이 같은 행을 바꿨을 수 있다 — 떼어진 엔티티를 merge하지 않고 다시 읽어 채운다.
        // 그 사이 숫자가 달라져 이유가 비워졌다면 이 문장은 이미 옛 숫자를 인용한 것이라 덮어쓰지 않는다.
        transactionTemplate.executeWithoutResult(status ->
                suggestionRepository.findProposedByUserIdAndBehaviorId(userId, behaviorId)
                        .filter(fresh -> fresh.getReason() == null)
                        .filter(fresh -> fresh.getId().equals(target.get().getId()))
                        .ifPresent(fresh -> fresh.explain(reason)));
    }

    /** AI가 없거나(503) 설명이 아닌 문장을 주면 빈 문자열이다 — 채우지 않고 템플릿에 맡긴다 (E-38). */
    private String resolveReason(long userId, long suggestionId) {
        try {
            ChatResponse response = aiClient.chat(new ChatRequest(
                    REASON_MESSAGE,
                    String.valueOf(userId),
                    new TaskContext(TaskType.ACTION_PLAN, RetrospectStatus.ACTIVE,
                            Map.of("suggestion_ids", List.of(suggestionId))),
                    List.of()));
            if (!isExplanation(response)) {
                return "";
            }
            return response.reply() == null ? "" : response.reply().strip();
        } catch (BusinessException llmUnavailable) {
            return "";
        }
    }

    /**
     * 이 응답이 <b>제안 설명</b>인지. 묶음 이름(CLUSTER_NAMING)과 달리 폴백 문장을 그대로 쓸 수 없다 —
     * ACTION_PLAN의 폴백은 "지금은 설명드릴 수 없어요"라는 안내라, 저장하면 이유 자리에 사과문이 남는다.
     *
     * <p>AI가 Spring 내부 조회에 실패했을 때도 같다. 그때는 {@code tool_results[].success = false}이고
     * {@code reply}는 데이터 없이 진행 가능한 문장이다 (05 §3 타임아웃 표).
     */
    private boolean isExplanation(ChatResponse response) {
        if (response.isFallback()) {
            return false;
        }
        List<Map<String, Object>> toolResults = response.toolResults();
        if (toolResults == null) {
            return true;
        }
        return toolResults.stream().noneMatch(result -> result != null && Boolean.FALSE.equals(result.get("success")));
    }
}
