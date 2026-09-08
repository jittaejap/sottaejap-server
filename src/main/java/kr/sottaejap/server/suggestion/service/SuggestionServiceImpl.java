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
        List<Suggestion> suggestions = suggestionRepository.findAllByUserId(userId).stream()
                .filter(suggestion -> status == null
                        ? DEFAULT_STATUSES.contains(suggestion.getStatus())
                        : suggestion.getStatus() == status)
                .toList();

        Map<Long, ClusterSnapshot> clusters = clustersOf(suggestions);
        // 좌표 순 → 부담 내림차순 (E-81). 마지막 동점은 id로 끊는다 — 규칙 계층은 id를 모른다
        Comparator<Suggestion> order = Comparator
                .comparing((Suggestion suggestion) -> clusters.get(suggestion.getBehaviorId()),
                        Comparator.nullsLast(SuggestionOrderRule.comparator()))
                .thenComparing(Suggestion::getId);

        return new SuggestionListResponse(suggestions.stream()
                .sorted(order)
                .map(suggestion -> view(suggestion, clusters.get(suggestion.getBehaviorId())))
                .toList());
    }

    @Override
    public SuggestionListResponse internalList(long userId) {
        return list(userId, null);
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
                SuggestionReasonTemplate.reasonFor(name, cluster));
    }
}
