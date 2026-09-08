package kr.sottaejap.server.rules;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.AnalysisAggregator;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import kr.sottaejap.server.rules.report.GoalAllocation;
import kr.sottaejap.server.rules.report.GoalSaving;
import kr.sottaejap.server.rules.report.MonthlyDeltaRule;
import kr.sottaejap.server.rules.report.MonthlyFigures;
import kr.sottaejap.server.rules.report.MonthlyTransaction;
import kr.sottaejap.server.rules.saving.GoalProjectionRule;
import kr.sottaejap.server.rules.saving.SavingRule;
import kr.sottaejap.server.rules.saving.SuggestionOrderRule;
import kr.sottaejap.server.rules.saving.SuggestionTargetRule;
import kr.sottaejap.server.rules.cluster.ClusterEngine;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.rules.cluster.ClusterRecomputeInput;
import kr.sottaejap.server.rules.cluster.ClusterRecomputeResult;
import kr.sottaejap.server.rules.cluster.RetrospectedTransaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR-01 증명 (E-29) — 같은 입력에 같은 출력.
 *
 * <p>규칙 엔진 코드가 생기기 전에는 "비결정 요소를 참조하지 않는다"는 구조적 조건만 검사한다.
 * 규칙이 하나씩 붙을 때마다 그 규칙의 입력 → 출력 고정 케이스를 이 클래스에 추가한다.
 */
class RuleEngineDeterminismTest {

    private static final Path RULES_SOURCE_ROOT = Path.of("src/main/java/kr/sottaejap/server/rules");

    /** HTTP·LLM·난수·현재시각 (07 §1 규칙 엔진 행). */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "java.util.Random",
            "ThreadLocalRandom",
            "Math.random",
            "SecureRandom",
            "UUID.randomUUID",
            "LocalDateTime.now",
            "LocalDate.now",
            "Instant.now",
            "OffsetDateTime.now",
            "ZonedDateTime.now",
            "System.currentTimeMillis",
            "Clock.system",
            "RestClient",
            "RestTemplate",
            "WebClient",
            "HttpClient",
            "kr.sottaejap.server.ai"
    );

    @Test
    void rulesPackage_doesNotReferenceNonDeterministicSources() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RULES_SOURCE_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, violations));
        }
        assertTrue(violations.isEmpty(), () -> "규칙 엔진은 결정론이어야 합니다:\n" + String.join("\n", violations));
    }

    @Test
    void ruleParams_sameInput_sameValue() {
        assertEquals(sampleParams(), sampleParams());
    }

    @Test
    void ruleParams_pendingMinCount가_rollupMinCount보다_크면_거부한다() {
        assertThrows(IllegalStateException.class,
                () -> new RuleParams(3.0, 3, 4, 0.1, 0.0, 3, null, null, null));
    }

    @Test
    void ruleParams_require는_null이면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> RuleParams.require(null, "rules.shrinkage-k"));
        assertTrue(exception.getMessage().contains("rules.shrinkage-k"));
        assertEquals(3.0, RuleParams.require(3.0, "rules.shrinkage-k"));
    }

    static RuleParams sampleParams() {
        return RuleParamsFixture.sample();
    }

    /** ClusterEngine 골든 케이스 — 같은 입력을 두 번 넣으면 같은 출력 (NFR-01 · E-61). */
    @Test
    void clusterEngine_고정입력_고정출력() {
        ClusterRecomputeInput input = new ClusterRecomputeInput(1_000_000, YearMonth.of(2026, 8), List.of(
                new RetrospectedTransaction(1, "배달의민족", "배달", TimeSlot.NIGHT, 12000, YearMonth.of(2026, 8), "충동", "혼자", Satisfaction.LOW),
                new RetrospectedTransaction(2, "쿠팡이츠", "배달", TimeSlot.NIGHT, 15000, YearMonth.of(2026, 8), "충동", "혼자", Satisfaction.LOW),
                new RetrospectedTransaction(3, "스타벅스", "카페", TimeSlot.DAY, 6000, YearMonth.of(2026, 8), "휴식·취미", "친구", Satisfaction.HIGH),
                new RetrospectedTransaction(4, "지하철", "교통", TimeSlot.MORNING, 1500, YearMonth.of(2026, 7), "필수품", "혼자", Satisfaction.UNKNOWN)));

        ClusterRecomputeResult first = ClusterEngine.recompute(input, sampleParams());
        ClusterRecomputeResult second = ClusterEngine.recompute(input, sampleParams());

        assertEquals(first, second);
        assertEquals(List.of("교통|||", "배달|NIGHT||", "카페|DAY||", "교통||필수품|혼자", "배달|NIGHT|충동|혼자", "카페|DAY|휴식·취미|친구"),
                first.clusters().stream().map(ClusterEvaluation::clusterKey).toList());
        assertEquals(-1.0 / 3, first.userAverage(), 1e-9);
    }

    /** AnalysisAggregator 골든 케이스 — 같은 묶음·같은 예산이면 같은 집계 (NFR-01 · E-73). */
    @Test
    void analysisAggregator_고정입력_고정출력() {
        List<ClusterSnapshot> clusters = List.of(
                new ClusterSnapshot(1L, "배달|NIGHT||", "심야 배달", null, 4, -0.42, 12000, 96000, 8, 0.096,
                        EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST),
                new ClusterSnapshot(2L, "카페|DAY||", "낮 카페", null, 5, 0.71, 28000, 168000, 6, 0.168,
                        EvaluationStatus.RESOLVED, Quadrant.PROTECT, Verdict.SUSTAIN),
                new ClusterSnapshot(3L, "편의점|||", null, null, 1, -0.05, 3000, 24000, 8, 0.024,
                        EvaluationStatus.PENDING, null, null));

        AnalysisSummary first = AnalysisAggregator.aggregate(clusters, 1_000_000);
        AnalysisSummary second = AnalysisAggregator.aggregate(clusters, 1_000_000);

        assertEquals(first, second);
        assertEquals(List.of("카페", "배달", "편의점"),
                first.byCategory().stream().map(CategorySummary::category).toList());
        assertEquals(0.096, first.byVerdict().get(1).share(), 1e-9);
        assertEquals(24000, first.pending().monthlyTotalAmount());
    }

    /** 절감액 규칙 골든 케이스 — 같은 묶음이면 같은 대상 판정·같은 금액·같은 순서 (NFR-01 · E-81). */
    @Test
    void savingRule_고정입력_고정출력() {
        List<ClusterSnapshot> clusters = List.of(
                new ClusterSnapshot(1L, "배달|NIGHT||", "심야 배달", null, 4, -0.42, 12000, 96000, 8, 0.096,
                        EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST),
                new ClusterSnapshot(2L, "카페|DAY||", "낮 카페", null, 5, 0.71, 28000, 168000, 6, 0.168,
                        EvaluationStatus.RESOLVED, Quadrant.PROTECT, Verdict.SUSTAIN),
                new ClusterSnapshot(3L, "택시|||", null, null, 4, -0.9, 20000, 80000, 4, 0.3,
                        EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST));

        List<Long> targets = clusters.stream().filter(SuggestionTargetRule::isTarget)
                .sorted(SuggestionOrderRule.comparator()).map(ClusterSnapshot::id).toList();

        assertEquals(List.of(3L, 1L), targets);
        assertEquals(96000, SavingRule.expectedSaving(12000, 8));
        assertEquals(0.096, GoalProjectionRule.projectedRate(0, 96000, 1_000_000), 1e-9);
        assertEquals(targets, clusters.stream().filter(SuggestionTargetRule::isTarget)
                .sorted(SuggestionOrderRule.comparator()).map(ClusterSnapshot::id).toList());
    }

    /** 월간 산식 골든 케이스 — 같은 거래 · 같은 목표면 같은 집계 · 같은 배분 (NFR-01 · E-94). */
    @Test
    void monthlyDeltaRule_고정입력_고정출력() {
        List<MonthlyTransaction> transactions = List.of(
                new MonthlyTransaction(12000, YearMonth.of(2026, 8), Satisfaction.LOW, true),
                new MonthlyTransaction(15000, YearMonth.of(2026, 8), Satisfaction.LOW, true),
                new MonthlyTransaction(6000, YearMonth.of(2026, 8), Satisfaction.HIGH, false),
                new MonthlyTransaction(4000, YearMonth.of(2026, 8), null, false),
                new MonthlyTransaction(48000, YearMonth.of(2026, 7), null, false));
        List<GoalSaving> savings = List.of(new GoalSaving(2L, 10000), new GoalSaving(1L, 30000));

        MonthlyFigures august = MonthlyDeltaRule.figures(transactions, YearMonth.of(2026, 8));
        MonthlyFigures july = MonthlyDeltaRule.figures(transactions, YearMonth.of(2026, 7));
        Integer saved = MonthlyDeltaRule.savedAmount(july.totalSpending(), august.totalSpending());
        List<GoalAllocation> first = MonthlyDeltaRule.allocate(saved, savings);
        List<GoalAllocation> second = MonthlyDeltaRule.allocate(saved, savings);

        assertEquals(new MonthlyFigures(37000, 2, 2), august);
        assertEquals(11000, saved);
        assertEquals(List.of(new GoalAllocation(1L, 8250), new GoalAllocation(2L, 2750)), first);
        assertEquals(first, second);
    }

    private static void collectViolations(Path path, List<String> violations) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String token : FORBIDDEN_TOKENS) {
                if (source.contains(token)) {
                    violations.add(path + " → " + token);
                }
            }
        } catch (IOException exception) {
            violations.add(path + " → 읽기 실패: " + exception.getMessage());
        }
    }
}
