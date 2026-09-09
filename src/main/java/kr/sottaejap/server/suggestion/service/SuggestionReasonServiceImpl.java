package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 *
 * <p><b>응답 경로 밖에서 돈다 ({@code @Async}).</b> 이유 문장은 {@code RetrospectSaveResponse}에 실리지 않으므로
 * 사용자를 기다리게 할 까닭이 없다. 동기로 두면 저장 1건의 AI 왕복이 명명 15초 + 이유 15초로 최악 30초가 되어,
 * 클라이언트 공통 타임아웃 20초(`httpClient.ts`)가 먼저 끊는다 — 회고는 이미 커밋돼 있어 사용자는 실패로 보고
 * 다시 저장하면 {@code DUPLICATE_RETROSPECT}를 받는다. 비동기로 빼면 저장 응답 시간은 명명 1회 그대로이고
 * E-64의 "저장 1건당 왕복 1회"도 응답 기준으로는 유지된다 (PR #46 리뷰 2).
 */
@Slf4j
@Service
public class SuggestionReasonServiceImpl implements SuggestionReasonService {

    /** AI는 task_context.state로 판단한다 — message는 작업을 알리는 한 문장이면 된다 (05 §3). */
    private static final String REASON_MESSAGE = "이 제안을 하게 된 이유를 설명해 주세요";

    private final SuggestionRepository suggestionRepository;
    private final BehaviorClusterRepository behaviorClusterRepository;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    public SuggestionReasonServiceImpl(SuggestionRepository suggestionRepository,
                                       BehaviorClusterRepository behaviorClusterRepository, AiClient aiClient,
                                       PlatformTransactionManager transactionManager) {
        this.suggestionRepository = suggestionRepository;
        this.behaviorClusterRepository = behaviorClusterRepository;
        this.aiClient = aiClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Async("suggestionReasonExecutor")
    public void explainProposed(long userId, long leafId) {
        try {
            explain(userId, leafId);
        } catch (RuntimeException failed) {
            // 응답 밖이라 올려 봐야 받을 곳이 없다. 이유가 비면 화면이 템플릿으로 채운다 (E-38).
            log.warn("제안 이유를 채우지 못했습니다. userId={} leafId={}", userId, leafId, failed);
        }
    }

    private void explain(long userId, long leafId) {
        Snapshot target = transactionTemplate.execute(status -> snapshot(userId, leafId));
        if (target == null) {
            return;
        }

        String reason = resolveReason(userId, target.suggestionId());
        if (reason.isBlank()) {
            return;
        }
        // AI 왕복 동안 재계산이 같은 행을 바꿨을 수 있다 — 떼어진 엔티티를 merge하지 않고 다시 읽어 채운다.
        // 숫자가 달라졌으면 이 문장은 옛 값을 인용한 것이라 버린다. reason이 null인지로는 구별할 수 없다 —
        // 첫 조회 조건이 이미 null이라, applyNumbers가 비운 것과 처음부터 비어 있던 것이 같아 보인다 (리뷰 3).
        transactionTemplate.executeWithoutResult(status ->
                suggestionRepository.findProposedByUserIdAndBehaviorId(userId, target.behaviorId())
                        .filter(fresh -> fresh.getId().equals(target.suggestionId()))
                        .filter(fresh -> fresh.getReason() == null)
                        .filter(target::sameNumbers)
                        .ifPresent(fresh -> fresh.explain(reason)));
    }

    /**
     * 이유를 채울 제안을 찾는다. 인자로 오는 것은 {@code RetrospectWriter}가 돌려준 <b>리프</b> id인데,
     * 리프 회고 수가 {@code rules.rollup-min-count} 미만이면 리프는 상위 묶음에 붙고 제안은 <b>상위 묶음</b>에
     * 달린다 — 유효 묶음은 {@code parentId is null}인 것뿐이기 때문이다 (E-59 · E-72). 리프 id로만 찾으면
     * 그 경우 아무것도 못 찾고, 회고를 더 저장해도 늘 리프 id로 들어와 영영 채워지지 않는다 (리뷰 1).
     *
     * <p>롤업은 한 단계다 — {@code ClusterRecomputeServiceImpl}이 {@code parentKey} → id를 한 번만 매핑한다.
     * 그래서 부모를 한 번만 따라가면 된다.
     */
    private Snapshot snapshot(long userId, long leafId) {
        long behaviorId = behaviorClusterRepository.findByIdAndUserId(leafId, userId)
                .map(leaf -> leaf.getParentId() == null ? leafId : leaf.getParentId())
                .orElse(leafId);
        return suggestionRepository.findProposedByUserIdAndBehaviorId(userId, behaviorId)
                .filter(suggestion -> suggestion.getReason() == null)
                .map(suggestion -> new Snapshot(behaviorId, suggestion.getId(),
                        suggestion.getAdjustCount(), suggestion.getExpectedSaving()))
                .orElse(null);
    }

    /** AI를 부르기 전에 붙잡아 둔 값. 왕복 뒤에도 같은 제안·같은 숫자일 때만 문장을 채운다. */
    private record Snapshot(long behaviorId, Long suggestionId, Integer adjustCount, Integer expectedSaving) {

        boolean sameNumbers(Suggestion fresh) {
            return Objects.equals(adjustCount, fresh.getAdjustCount())
                    && Objects.equals(expectedSaving, fresh.getExpectedSaving());
        }
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
