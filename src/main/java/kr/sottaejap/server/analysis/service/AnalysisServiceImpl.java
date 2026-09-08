package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.dto.MapPointView;
import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;
import kr.sottaejap.server.rules.RuleParams;
import kr.sottaejap.server.rules.aggregate.AnalysisAggregator;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.ClusterOrderRule;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 지도 · 분석 조회 (⑧ · API 14 · 22).
 *
 * <p><b>클래스에 {@code @Transactional}이 없다.</b> {@link #analysis(long)}이 AI를 부르고, 그 왕복이 최대
 * 15초라 트랜잭션을 열어 둔 채 기다릴 수 없다 — 읽기는 {@link AnalysisSnapshotLoader} 안에서 끝난다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final String AXIS_X_LABEL = "지출 부담";
    private static final String AXIS_X_FORMULA = "MONTHLY_TOTAL_OVER_BUDGET";
    private static final String AXIS_Y_LABEL = "보정 만족도";
    /** 보정 만족도의 정의역 [-1, 1] (04 §3) — 튜닝값이 아니라 축의 정의다. */
    private static final List<Integer> AXIS_Y_RANGE = List.of(-1, 1);

    private final AnalysisSnapshotLoader snapshotLoader;
    private final HighlightService highlightService;
    private final RuleParams ruleParams;

    @Override
    public SatisfactionMapResponse satisfactionMap(long userId) {
        AnalysisSnapshot snapshot = snapshotLoader.load(userId);
        return new SatisfactionMapResponse(
                snapshot.analysisYearMonthText(),
                new SatisfactionMapResponse.AxisXView(AXIS_X_LABEL, AXIS_X_FORMULA, snapshot.monthlyBudget()),
                new SatisfactionMapResponse.AxisYView(AXIS_Y_LABEL, AXIS_Y_RANGE),
                // 경계는 잠정값이라도 그대로 내려준다 (E-74). 축을 안 그리는 것보다 임시 축이 낫다는 판단이다.
                new SatisfactionMapResponse.BoundariesView(ruleParams.axisXBoundary(), ruleParams.axisYBoundary()),
                points(snapshot.clusters()));
    }

    @Override
    public AnalysisResponse analysis(long userId) {
        AnalysisSnapshot snapshot = snapshotLoader.load(userId);
        AnalysisSummary summary = AnalysisAggregator.aggregate(snapshot.clusters(), snapshot.monthlyBudget());
        return new AnalysisResponse(
                snapshot.analysisYearMonthText(),
                summary.byVerdict(),
                summary.pending(),
                summary.byCategory(),
                highlight(userId, snapshot, summary));
    }

    @Override
    public InternalAnalysisResponse internalAnalysis(long userId) {
        AnalysisSnapshot snapshot = snapshotLoader.load(userId);
        AnalysisSummary summary = AnalysisAggregator.aggregate(snapshot.clusters(), snapshot.monthlyBudget());
        return new InternalAnalysisResponse(
                snapshot.analysisYearMonthText(),
                summary.byVerdict(),
                summary.pending(),
                summary.byCategory(),
                points(snapshot.clusters()));
    }

    /**
     * 재구성할 수치가 없으면 AI를 부르지 않는다 (E-75 · E-91) — 할 말이 정해져 있는데 15초를 쓸 이유가 없다.
     *
     * <p>조건이 둘이다. <b>돌아본 소비가 하나도 없거나</b>(E-75), <b>회고는 했지만 그 거래가 전부 기준월 밖이라
     * {@code byCategory}가 통째로 빈 경우</b>다(E-91). 뒤쪽에서 AI가 받는 state는 {@code by_category []}에 금액이
     * 전부 0이라 무엇을 쓰든 집계 밖 서술이 되고(NFR-02), 그것을 거를 숫자 가드(E-79 · 06 R19)는 {@code ai}에 아직
     * 없다. 두 조건 모두에서 {@link HighlightTemplate#highlightFor}는 문장 하나로 정해져 있다.
     *
     * <p>앞 조건은 뒤 조건에 포함되지만 남겨 둔다 — E-75와 E-91이 코드에 그대로 보이고, {@code byCategory}에서
     * 합계 0을 빼는 규칙(E-73)이 바뀌어도 E-75가 혼자 선다.
     */
    private String highlight(long userId, AnalysisSnapshot snapshot, AnalysisSummary summary) {
        if (snapshot.clusters().isEmpty() || summary.byCategory().isEmpty()) {
            return HighlightTemplate.highlightFor(summary);
        }
        return highlightService.highlight(userId, snapshot.analysisYearMonth(), summary);
    }

    private List<MapPointView> points(List<ClusterSnapshot> clusters) {
        return clusters.stream()
                .sorted(ClusterOrderRule.mapOrder())
                .map(MapPointView::from)
                .toList();
    }
}
