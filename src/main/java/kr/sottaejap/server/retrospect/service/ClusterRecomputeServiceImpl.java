package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction;
import kr.sottaejap.server.rules.RuleParams;
import kr.sottaejap.server.rules.cluster.ClusterEngine;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.rules.cluster.ClusterRecomputeInput;
import kr.sottaejap.server.rules.cluster.ClusterRecomputeResult;
import kr.sottaejap.server.rules.cluster.RetrospectedTransaction;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 전체 묶음 재계산 (E-61). 회고를 하나 저장할 때마다 사용자 평균이 바뀌고 상위 묶음은 자식의 합집합이라
 * 부분 재계산이 더 어렵다 — 그래서 매번 전부 다시 계산한다.
 *
 * <p>저장 순서가 계약이다. {@code ClusterEngine}이 상위 묶음을 먼저 주므로 그 순서대로 저장해 id를 얻고,
 * 리프는 그 id를 {@code parentId}로 받는다 (E-59). 이번 결과에 없는 기존 묶음은 지우지 않고 값만 비운다
 * ({@link BehaviorCluster#markEmpty}) — {@code transactions.behavior_id}와 제안이 그 행을 참조할 수 있고,
 * 옛 값이 남으면 지도에 유령 점이 생긴다 (9/7 실측).
 */
@Service
@RequiredArgsConstructor
public class ClusterRecomputeServiceImpl implements ClusterRecomputeService {

    private final RetrospectRepository retrospectRepository;
    private final BehaviorClusterRepository behaviorClusterRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final RuleParams ruleParams;

    @Override
    @Transactional
    public List<BehaviorCluster> recomputeAll(long userId) {
        // analysisYearMonth = 사용자의 최근 거래월 (E-60). 거래가 없으면 회고도 있을 수 없다.
        YearMonth analysisYearMonth = transactionRepository.findTopByUserIdOrderByOccurredAtDesc(userId)
                .map(ClusterRecomputeServiceImpl::yearMonthOf)
                .orElse(null);
        if (analysisYearMonth == null) {
            return List.of();
        }

        List<RetrospectedTransaction> transactions = retrospectRepository.findAllWithTransactionByUserId(userId).stream()
                .map(ClusterRecomputeServiceImpl::toRuleInput)
                .toList();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        ClusterRecomputeResult result = ClusterEngine.recompute(
                new ClusterRecomputeInput(user.getMonthlyBudget(), analysisYearMonth, transactions), ruleParams);

        List<BehaviorCluster> saved = new ArrayList<>();
        Map<String, Long> idsByKey = new HashMap<>();
        for (ClusterEvaluation evaluation : result.clusters()) {
            BehaviorCluster cluster = behaviorClusterRepository
                    .findByUserIdAndClusterKey(userId, evaluation.clusterKey())
                    .orElseGet(() -> BehaviorCluster.create(userId, evaluation.clusterKey()));
            cluster.apply(evaluation, evaluation.parentKey() == null ? null : idsByKey.get(evaluation.parentKey()));

            BehaviorCluster stored = behaviorClusterRepository.save(cluster);
            idsByKey.put(evaluation.clusterKey(), stored.getId());
            saved.add(stored);
            // 직접 구성원만 배정한다 — 상위 묶음의 합집합은 리프가 이미 가져간다 (E-59).
            assignBehavior(evaluation.transactionIds(), stored.getId());
        }

        behaviorClusterRepository.findAllByUserIdOrderByClusterKeyAsc(userId).stream()
                .filter(existing -> !idsByKey.containsKey(existing.getClusterKey()))
                .filter(existing -> !existing.isEmpty())
                .forEach(BehaviorCluster::markEmpty);

        user.updateAvgSatisfaction(result.userAverage());
        return List.copyOf(saved);
    }

    /** 더티 체킹으로 갱신한다 — 트랜잭션 안이라 별도 save가 필요 없다. */
    private void assignBehavior(List<Long> transactionIds, Long behaviorId) {
        if (transactionIds.isEmpty()) {
            return;
        }
        transactionRepository.findAllById(transactionIds)
                .forEach(transaction -> transaction.assignBehavior(behaviorId));
    }

    private static RetrospectedTransaction toRuleInput(RetrospectWithTransaction row) {
        Transaction transaction = row.transaction();
        Retrospect retrospect = row.retrospect();
        return new RetrospectedTransaction(
                transaction.getId(),
                transaction.getMerchant(),
                transaction.getCategory(),
                transaction.getTimeSlot(),
                transaction.getAmount(),
                yearMonthOf(transaction),
                retrospect.getPurpose(),
                retrospect.getCompanion(),
                retrospect.getSatisfaction());
    }

    /** DB의 OffsetDateTime은 UTC로 정규화돼 있다 — 월은 KST로 본다. */
    private static YearMonth yearMonthOf(Transaction transaction) {
        return YearMonth.from(transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE));
    }
}
