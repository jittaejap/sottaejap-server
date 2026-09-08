package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.RuleParamsFixture;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 지도 · 분석 조회 (⑧ · API 14 · 22). */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private AnalysisSnapshotLoader snapshotLoader;
    @Mock
    private HighlightService highlightService;

    private AnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnalysisServiceImpl(snapshotLoader, highlightService, RuleParamsFixture.sample());
    }

    @Test
    void 돌아본_소비가_없으면_AI를_부르지_않는다() {
        when(snapshotLoader.load(USER_ID)).thenReturn(snapshot(1_000_000, List.of()));

        AnalysisResponse response = service.analysis(USER_ID);

        assertEquals(HighlightTemplate.NO_RETROSPECT, response.highlight());
        verifyNoInteractions(highlightService);
    }

    @Test
    void 묶음이_있으면_집계를_AI에_넘겨_한_문장을_받는다() {
        when(snapshotLoader.load(USER_ID)).thenReturn(snapshot(1_000_000, List.of(adjust(), sustain())));
        when(highlightService.highlight(anyLong(), any(), any())).thenReturn("나만의 특징");

        AnalysisResponse response = service.analysis(USER_ID);

        assertEquals("나만의 특징", response.highlight());
        assertEquals(0.096, adjustRow(response), 1e-9);
        assertEquals("2026-08", response.analysisYearMonth());
    }

    @Test
    void 예산이_없으면_share가_null이다() {
        when(snapshotLoader.load(USER_ID)).thenReturn(snapshot(null, List.of(adjust())));
        when(highlightService.highlight(anyLong(), any(), any())).thenReturn("나만의 특징");

        AnalysisResponse response = service.analysis(USER_ID);

        assertNull(response.byVerdict().get(1).share());
        assertNull(response.pending().share());
    }

    @Test
    void 지도는_경계값을_그대로_내려주고_AI를_부르지_않는다() {
        when(snapshotLoader.load(USER_ID)).thenReturn(snapshot(1_000_000, List.of(adjust(), sustain())));

        SatisfactionMapResponse map = service.satisfactionMap(USER_ID);

        assertEquals(0.1, map.boundaries().x());
        assertEquals(0.0, map.boundaries().y());
        assertEquals(1_000_000, map.axisX().monthlyBudget());
        // 바꿔볼 소비가 먼저 온다
        assertEquals(List.of(1L, 2L), map.points().stream().map(point -> point.behaviorId()).toList());
        verifyNoInteractions(highlightService);
    }

    @Test
    void 내부_AI_응답에는_highlight가_없고_points가_들어_있다() {
        when(snapshotLoader.load(USER_ID)).thenReturn(snapshot(1_000_000, List.of(adjust(), sustain())));

        InternalAnalysisResponse response = service.internalAnalysis(USER_ID);

        assertEquals(2, response.points().size());
        assertEquals(2, response.byVerdict().size());
        verifyNoInteractions(highlightService);
    }

    private static double adjustRow(AnalysisResponse response) {
        return response.byVerdict().stream()
                .filter(row -> row.verdict() == Verdict.ADJUST)
                .findFirst().orElseThrow().share();
    }

    private static AnalysisSnapshot snapshot(Integer budget, List<ClusterSnapshot> clusters) {
        return new AnalysisSnapshot(YearMonth.of(2026, 8), budget, clusters);
    }

    private static ClusterSnapshot adjust() {
        return new ClusterSnapshot(1L, "배달|NIGHT||", "심야 배달", null, 4, -0.42, 12_000, 96_000, 8, 0.096,
                EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST);
    }

    private static ClusterSnapshot sustain() {
        return new ClusterSnapshot(2L, "카페|DAY||", "낮 카페", null, 5, 0.71, 28_000, 168_000, 6, 0.168,
                EvaluationStatus.RESOLVED, Quadrant.PROTECT, Verdict.SUSTAIN);
    }
}
