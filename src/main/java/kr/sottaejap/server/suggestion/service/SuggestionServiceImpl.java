package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.analysis.service.ClusterSnapshotMapper;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.service.ClusterNameTemplate;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import kr.sottaejap.server.rules.saving.SavingRule;
import kr.sottaejap.server.rules.saving.SuggestionOrderRule;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionListResponse;
import kr.sottaejap.server.suggestion.dto.SuggestionView;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 제안 목록 · 채택 · 거절 (⑦ · 05 §2 #15·#16). 만드는 일은 {@link SuggestionSyncService}가 한다 (E-81). */
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    /** 기본 목록 — 거절한 제안은 빼고 본다 (05 §2). */
    private static final Set<SuggestionStatus> DEFAULT_STATUSES =
            Set.of(SuggestionStatus.PROPOSED, SuggestionStatus.ADOPTED);

    private final SuggestionRepository suggestionRepository;
    private final BehaviorClusterRepository behaviorClusterRepository;
    private final GoalRepository goalRepository;

    @Override
    @Transactional(readOnly = true)
    public SuggestionListResponse list(long userId, SuggestionStatus status) {
        return list(userId, status, true);
    }

    /** {@code list}를 자기 호출하므로 프록시를 거치지 않는다 — 트랜잭션을 여기에 따로 건다. */
    @Override
    @Transactional(readOnly = true)
    public SuggestionListResponse internalList(long userId) {
        return list(userId, null, false);
    }

    /**
     * @param storedReason 저장된 AI 문장을 쓸지. <b>내부 AI 조회는 항상 {@code false}다</b> — AI가 이 목록의
     *                     {@code reason}을 프롬프트에 넣으므로, 자기가 쓴 문장을 돌려주면 자기 출력을 근거로
     *                     삼는다 (E-75 — {@code GET /analysis}에서 {@code highlight}를 뺀 것과 같은 이유).
     */
    private SuggestionListResponse list(long userId, SuggestionStatus status, boolean storedReason) {
        List<Suggestion> suggestions = suggestionRepository.findAllByUserId(userId).stream()
                .filter(suggestion -> status == null
                        ? DEFAULT_STATUSES.contains(suggestion.getStatus())
                        : suggestion.getStatus() == status)
                .toList();

        // 조회가 behavior_clusters 조인이고 behavior_id가 FK라 묶음은 반드시 있다 — null을 방어하지 않는다
        Map<Long, ClusterSnapshot> clusters = clustersOf(suggestions);
        // 좌표 순 → 부담 내림차순 (E-81). 마지막 동점은 id로 끊는다 — 규칙 계층은 id를 모른다
        Comparator<Suggestion> order = Comparator
                .comparing((Suggestion suggestion) -> clusters.get(suggestion.getBehaviorId()),
                        SuggestionOrderRule.comparator())
                .thenComparing(Suggestion::getId);

        return new SuggestionListResponse(suggestions.stream()
                .sorted(order)
                .map(suggestion -> view(suggestion, clusters.get(suggestion.getBehaviorId()), storedReason))
                .toList());
    }

    @Override
    @Transactional
    public SuggestionView adopt(long userId, long suggestionId, SuggestionAdoptRequest request) {
        Suggestion suggestion = find(userId, suggestionId);
        rejectIsFinal(suggestion);

        ClusterSnapshot cluster = clusterOf(suggestion);
        if (cluster.avgAmount() == null || !SavingRule.isValidAdjustCount(request.adjustCount(), cluster.txCount())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (request.goalId() != null) {
            goalRepository.findByIdAndUserIdAndDeletedAtIsNull(request.goalId(), userId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        }

        suggestion.adopt(request.adjustCount(),
                SavingRule.expectedSaving(cluster.avgAmount(), request.adjustCount()),
                request.goalId());
        return view(suggestion, cluster);
    }

    @Override
    @Transactional
    public SuggestionView reject(long userId, long suggestionId) {
        Suggestion suggestion = find(userId, suggestionId);
        rejectIsFinal(suggestion);

        suggestion.reject();
        return view(suggestion, clusterOf(suggestion));
    }

    /** 없는 제안과 남의 제안을 구별해 주지 않는다 — 조회가 이미 사용자 조인이다. */
    private Suggestion find(long userId, long suggestionId) {
        return suggestionRepository.findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 거절은 종단이다 (E-82). 되살리려면 재제안이 필요한데 그건 P2다. */
    private void rejectIsFinal(Suggestion suggestion) {
        if (suggestion.getStatus() == SuggestionStatus.REJECTED) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    /** AI가 쓴 문장이 있으면 그것을, 없으면 판정 템플릿을 보여준다 (E-38 — ai가 내려가도 목록은 뜬다). */
    private static String reason(Suggestion suggestion, String name, ClusterSnapshot cluster, boolean storedReason) {
        if (storedReason && suggestion.getReason() != null) {
            return suggestion.getReason();
        }
        return SuggestionReasonTemplate.reasonFor(name, cluster);
    }

    private ClusterSnapshot clusterOf(Suggestion suggestion) {
        return behaviorClusterRepository.findById(suggestion.getBehaviorId())
                .map(ClusterSnapshotMapper::toSnapshot)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private Map<Long, ClusterSnapshot> clustersOf(List<Suggestion> suggestions) {
        List<Long> behaviorIds = suggestions.stream().map(Suggestion::getBehaviorId).distinct().toList();
        return behaviorClusterRepository.findAllById(behaviorIds).stream()
                .map(ClusterSnapshotMapper::toSnapshot)
                .collect(java.util.stream.Collectors.toMap(ClusterSnapshot::id, Function.identity()));
    }

    private SuggestionView view(Suggestion suggestion, ClusterSnapshot cluster) {
        return view(suggestion, cluster, true);
    }

    private SuggestionView view(Suggestion suggestion, ClusterSnapshot cluster, boolean storedReason) {
        String name = ClusterNameTemplate.displayNameOr(cluster.displayName(), cluster.clusterKey());
        return new SuggestionView(
                suggestion.getId(),
                cluster.id(),
                name,
                cluster.monthlyTotalAmount(),
                cluster.avgAmount(),
                cluster.txCount(),
                cluster.adjustedSatisfaction(),
                cluster.quadrant(),
                suggestion.getAdjustCount(),
                suggestion.getExpectedSaving(),
                suggestion.getGoalId(),
                suggestion.getStatus(),
                reason(suggestion, name, cluster, storedReason));
    }
}
