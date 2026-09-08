package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;

import java.util.List;

/**
 * AI `SpringClient.get_behavior_analysis`가 받는 것 (05 §3) — 외부 `GET /analysis` + 지도의 points다.
 *
 * <p><b>highlight가 없다</b> (E-75). 그 문장을 만드는 게 AI의 일이므로, 만들 문장을 미리 건네면
 * AI가 자기 출력을 근거로 삼는 꼴이 된다.
 */
public record InternalAnalysisResponse(
        String analysisYearMonth,
        List<VerdictSummary> byVerdict,
        PendingSummary pending,
        List<CategorySummary> byCategory,
        List<MapPointView> points
) {
}
