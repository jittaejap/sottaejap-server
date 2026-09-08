package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.analysis.service.ClusterSnapshotMapper;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import kr.sottaejap.server.rules.saving.SavingRule;
import kr.sottaejap.server.rules.saving.SuggestionTargetRule;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 제안을 재계산 결과에 맞춘다 (E-81).
 *
 * <p>세 가지를 한 번에 한다 — 대상이 아니게 된 {@code PROPOSED} 삭제, 남은 {@code PROPOSED} 갱신,
 * 새 대상에 {@code PROPOSED} 생성. <b>{@code ADOPTED}·{@code REJECTED}는 건드리지 않는다.</b>
 * 사용자가 정한 상태이고, 채택 금액은 "채택 당시 값"이라 재계산이 흔들면 본 숫자와 달라진다.
 *
 * <p>{@code recomputeAll}의 트랜잭션에 참여한다(REQUIRED) — 재계산과 제안이 따로 커밋되면
 * 지도와 제안 목록이 잠깐 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class SuggestionSyncServiceImpl implements SuggestionSyncService {

    private final BehaviorClusterRepository behaviorClusterRepository;
    private final SuggestionRepository suggestionRepository;

    @Override
    @Transactional
    public void sync(long userId) {
        Map<Long, ClusterSnapshot> targets = new LinkedHashMap<>();
        behaviorClusterRepository.findEffectiveByUserId(userId).stream()
                .map(ClusterSnapshotMapper::toSnapshot)
                .filter(SuggestionTargetRule::isTarget)
                .forEach(cluster -> targets.put(cluster.id(), cluster));

        List<Suggestion> existing = suggestionRepository.findAllByUserId(userId);

        // 사용자가 이미 정한 묶음에는 새 제안을 만들지 않는다 — 거절한 것이 다음 회고에 되살아나면 거절이 무의미하다
        Set<Long> decided = new HashSet<>();
        Map<Long, Suggestion> proposedByBehavior = new HashMap<>();
        for (Suggestion suggestion : existing) {
            if (suggestion.isDerived()) {
                proposedByBehavior.put(suggestion.getBehaviorId(), suggestion);
            } else {
                decided.add(suggestion.getBehaviorId());
            }
        }

        removeStale(proposedByBehavior, targets.keySet());

        for (ClusterSnapshot cluster : targets.values()) {
            if (decided.contains(cluster.id())) {
                continue;
            }
            int expectedSaving = SavingRule.expectedSaving(cluster.avgAmount(), cluster.txCount());
            Suggestion proposed = proposedByBehavior.get(cluster.id());
            if (proposed == null) {
                suggestionRepository.save(Suggestion.propose(cluster.id(), cluster.txCount(), expectedSaving));
            } else {
                proposed.refresh(cluster.txCount(), expectedSaving);
            }
        }
    }

    /**
     * 대상에서 빠진 제안을 지운다. <b>지운 뒤 곧바로 flush한다</b> — 부분 유일 인덱스
     * {@code uq_suggestions_proposed} 때문이다. Hibernate는 INSERT를 DELETE보다 먼저 내보내므로,
     * 같은 묶음의 제안을 지우고 새로 넣는 순서가 뒤집히면 유일 제약에 걸린다.
     */
    private void removeStale(Map<Long, Suggestion> proposedByBehavior, Set<Long> targetIds) {
        List<Suggestion> stale = proposedByBehavior.entrySet().stream()
                .filter(entry -> !targetIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(suggestion -> proposedByBehavior.remove(suggestion.getBehaviorId()));
        suggestionRepository.deleteAll(stale);
        suggestionRepository.flush();
    }
}
