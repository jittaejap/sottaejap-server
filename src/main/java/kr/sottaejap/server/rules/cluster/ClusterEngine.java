package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.rules.RuleParams;
import kr.sottaejap.server.rules.shrinkage.ShrinkageRule;
import kr.sottaejap.server.rules.verdict.BurdenRule;
import kr.sottaejap.server.rules.verdict.Evaluation;
import kr.sottaejap.server.rules.verdict.MonthlyBurden;
import kr.sottaejap.server.rules.verdict.VerdictRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ②③④ 합성 — 사용자 한 명의 회고 전체를 받아 묶음 평가 목록을 낸다 (E-61). 같은 입력이면 같은 출력이다.
 *
 * <p>순서: 거래를 id로 정렬 → 리프 키로 묶음 → 리프 회고 수가 기준 미만이면 상위 키(`카테고리|시간대||`)에 붙임(E-59)
 * → 상위 묶음은 붙은 자식 회고의 합집합으로 집계 → 각 묶음에 축소 추정·부담·판정.
 *
 * <p>목적·동행인이 둘 다 null인 회고는 리프 키가 상위 키와 같은 모양(`…||`)이 된다. 그런 회고는 상위 묶음의
 * <b>직접 구성원</b>으로 넣는다 — (userId, clusterKey) 유일 제약 때문에 같은 키의 묶음이 둘일 수 없다.
 * {@link ClusterEvaluation#transactionIds()}는 behaviorId를 배정할 직접 구성원이고, 집계는 합집합으로 한다.
 */
public final class ClusterEngine {

    private static final int SAMPLE_MERCHANT_LIMIT = 3;

    private ClusterEngine() {
    }

    public static ClusterRecomputeResult recompute(ClusterRecomputeInput input, RuleParams params) {
        List<RetrospectedTransaction> ordered = input.transactions().stream()
                .sorted(Comparator.comparingLong(RetrospectedTransaction::transactionId))
                .toList();
        double userAverage = ShrinkageRule.userAverage(ordered.stream().map(RetrospectedTransaction::satisfaction).toList());

        Map<String, List<RetrospectedTransaction>> leaves = new TreeMap<>();
        for (RetrospectedTransaction transaction : ordered) {
            String key = ClusterKeyRule.leafKey(transaction.category(), transaction.timeSlot(),
                    transaction.purpose(), transaction.companion(), params);
            leaves.computeIfAbsent(key, ignored -> new ArrayList<>()).add(transaction);
        }

        // 상위 키 → (직접 구성원, 합집합)
        Map<String, List<RetrospectedTransaction>> parentDirect = new TreeMap<>();
        Map<String, List<RetrospectedTransaction>> parentUnion = new TreeMap<>();
        Map<String, String> leafParent = new LinkedHashMap<>();

        for (Map.Entry<String, List<RetrospectedTransaction>> leaf : leaves.entrySet()) {
            String key = leaf.getKey();
            if (ClusterKeyRule.isParentKey(key)) {
                parentDirect.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(leaf.getValue());
                parentUnion.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(leaf.getValue());
                continue;
            }
            if (RollupRule.needsRollup(leaf.getValue().size(), params)) {
                String parentKey = ClusterKeyRule.parentKeyOf(key);
                leafParent.put(key, parentKey);
                parentDirect.computeIfAbsent(parentKey, ignored -> new ArrayList<>());
                parentUnion.computeIfAbsent(parentKey, ignored -> new ArrayList<>()).addAll(leaf.getValue());
            }
        }

        List<ClusterEvaluation> clusters = new ArrayList<>();
        for (String parentKey : parentUnion.keySet()) {
            clusters.add(evaluate(parentKey, null, parentUnion.get(parentKey), parentDirect.get(parentKey),
                    userAverage, input, params));
        }
        for (Map.Entry<String, List<RetrospectedTransaction>> leaf : leaves.entrySet()) {
            if (ClusterKeyRule.isParentKey(leaf.getKey())) {
                continue;
            }
            clusters.add(evaluate(leaf.getKey(), leafParent.get(leaf.getKey()), leaf.getValue(), leaf.getValue(),
                    userAverage, input, params));
        }
        return new ClusterRecomputeResult(userAverage, List.copyOf(clusters));
    }

    private static ClusterEvaluation evaluate(String clusterKey, String parentKey,
                                              List<RetrospectedTransaction> aggregate,
                                              List<RetrospectedTransaction> direct,
                                              double userAverage, ClusterRecomputeInput input, RuleParams params) {
        List<RetrospectedTransaction> members = aggregate.stream()
                .sorted(Comparator.comparingLong(RetrospectedTransaction::transactionId))
                .toList();
        List<Satisfaction> satisfactions = members.stream().map(RetrospectedTransaction::satisfaction).toList();
        Double rawAverage = ShrinkageRule.rawAverage(satisfactions);
        double adjusted = ShrinkageRule.adjust(ShrinkageRule.scoredCount(satisfactions), rawAverage, userAverage, params);
        MonthlyBurden burden = BurdenRule.of(members, input.analysisYearMonth(), input.monthlyBudget());
        Evaluation evaluation = VerdictRule.evaluate(members.size(), adjusted, burden.burdenRatio(), params);
        return new ClusterEvaluation(
                clusterKey,
                parentKey,
                members.size(),
                rawAverage,
                adjusted,
                burden.avgAmount(),
                burden.monthlyTotalAmount(),
                input.analysisYearMonth(),
                burden.txCount(),
                burden.burdenRatio(),
                evaluation.evaluationStatus(),
                evaluation.quadrant(),
                evaluation.verdict(),
                direct.stream().sorted(Comparator.comparingLong(RetrospectedTransaction::transactionId))
                        .map(RetrospectedTransaction::transactionId).toList(),
                members.stream().map(RetrospectedTransaction::merchant).distinct().limit(SAMPLE_MERCHANT_LIMIT).toList());
    }
}
