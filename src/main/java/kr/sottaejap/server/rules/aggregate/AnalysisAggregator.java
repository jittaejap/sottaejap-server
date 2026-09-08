package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 소비 분석 집계 (⑧ · E-73). 순수 함수다 — 같은 묶음 목록과 같은 예산이면 항상 같은 결과다 (NFR-01).
 *
 * <p>대상은 유효 묶음뿐이다 ({@link ClusterSnapshot#isEffective()} · E-72). 롤업된 리프를 함께 세면
 * 상위 묶음이 이미 들고 있는 금액을 두 번 세게 된다.
 *
 * <p>튜닝값이 없다 — {@code RuleParams}를 받지 않는 이유다. 경계값은 판정 단계에서 이미 반영돼
 * {@code quadrant} · {@code verdict}에 들어 있다.
 */
public final class AnalysisAggregator {

    private AnalysisAggregator() {
    }

    /**
     * @param clusters     사용자의 묶음 전부. 유효하지 않은 묶음은 여기서 걸러진다
     * @param monthlyBudget 월 예산. 없으면 모든 {@code share}가 null이다
     */
    public static AnalysisSummary aggregate(List<ClusterSnapshot> clusters, Integer monthlyBudget) {
        List<ClusterSnapshot> effective = clusters.stream().filter(ClusterSnapshot::isEffective).toList();
        return new AnalysisSummary(
                byVerdict(effective, monthlyBudget),
                pending(effective, monthlyBudget),
                byCategory(effective));
    }

    /** 판정 2종을 항상 낸다 — 해당 묶음이 없으면 0행이다. */
    private static List<VerdictSummary> byVerdict(List<ClusterSnapshot> effective, Integer monthlyBudget) {
        List<VerdictSummary> summaries = new ArrayList<>();
        for (Verdict verdict : Verdict.values()) {
            List<ClusterSnapshot> matched = effective.stream()
                    .filter(cluster -> cluster.verdict() == verdict)
                    .toList();
            int total = sum(matched);
            summaries.add(new VerdictSummary(verdict, matched.size(), total, share(total, monthlyBudget)));
        }
        return List.copyOf(summaries);
    }

    private static PendingSummary pending(List<ClusterSnapshot> effective, Integer monthlyBudget) {
        List<ClusterSnapshot> matched = effective.stream()
                .filter(cluster -> cluster.evaluationStatus() == EvaluationStatus.PENDING)
                .toList();
        int total = sum(matched);
        return new PendingSummary(matched.size(), total, share(total, monthlyBudget));
    }

    /**
     * 카테고리로 묶는다. 금액 합계에는 보류 묶음도 넣지만, 대표 판정은 RESOLVED 묶음에서만 고른다 —
     * 판정이 없는 묶음이 카테고리의 판정을 정할 수는 없다.
     */
    private static List<CategorySummary> byCategory(List<ClusterSnapshot> effective) {
        Map<String, List<ClusterSnapshot>> grouped = new TreeMap<>();
        for (ClusterSnapshot cluster : effective) {
            grouped.computeIfAbsent(cluster.category(), key -> new ArrayList<>()).add(cluster);
        }

        List<CategorySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<ClusterSnapshot>> entry : grouped.entrySet()) {
            List<ClusterSnapshot> members = entry.getValue();
            int total = sum(members);
            if (total == 0) {
                continue;
            }
            int txCount = members.stream().mapToInt(ClusterSnapshot::txCount).sum();
            summaries.add(new CategorySummary(
                    entry.getKey(),
                    dominantTimeSlot(members),
                    txCount == 0 ? null : total / txCount,
                    total,
                    dominantVerdict(members)));
        }

        summaries.sort(Comparator.comparingInt(CategorySummary::monthlyTotalAmount).reversed()
                .thenComparing(CategorySummary::category));
        return List.copyOf(summaries);
    }

    /** 합계가 가장 큰 시간대. 동률이면 enum 선언 순서(이른 시간대)를 쓴다. */
    private static TimeSlot dominantTimeSlot(List<ClusterSnapshot> members) {
        Map<TimeSlot, Integer> totals = new EnumMap<>(TimeSlot.class);
        for (ClusterSnapshot cluster : members) {
            TimeSlot timeSlot = cluster.timeSlot();
            if (timeSlot != null) {
                totals.merge(timeSlot, cluster.monthlyTotalAmount(), Integer::sum);
            }
        }
        return totals.entrySet().stream()
                .max(Comparator.<Map.Entry<TimeSlot, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().ordinal(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** 합계가 가장 큰 RESOLVED 묶음의 판정. 동률이면 묶음 키 순서로 고정한다. */
    private static Verdict dominantVerdict(List<ClusterSnapshot> members) {
        return members.stream()
                .filter(cluster -> cluster.verdict() != null)
                .max(Comparator.comparingInt(ClusterSnapshot::monthlyTotalAmount)
                        .thenComparing(ClusterSnapshot::clusterKey, Comparator.reverseOrder()))
                .map(ClusterSnapshot::verdict)
                .orElse(null);
    }

    private static int sum(List<ClusterSnapshot> clusters) {
        return clusters.stream().mapToInt(ClusterSnapshot::monthlyTotalAmount).sum();
    }

    /** 지출 부담의 분모는 월 예산이다 (E-73). 예산을 모르면 비율도 없다 — 0으로 대체하지 않는다. */
    private static Double share(int monthlyTotalAmount, Integer monthlyBudget) {
        if (monthlyBudget == null || monthlyBudget <= 0) {
            return null;
        }
        return (double) monthlyTotalAmount / monthlyBudget;
    }
}
