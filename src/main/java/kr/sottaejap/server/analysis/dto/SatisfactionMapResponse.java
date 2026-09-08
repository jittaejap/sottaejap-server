package kr.sottaejap.server.analysis.dto;

import java.util.List;

/**
 * 만족도 지도 (API 14 · FR-07). 축 정의를 같이 내려 화면이 축 문구를 따로 갖지 않게 한다.
 *
 * <p>{@code boundaries}는 `rules.axis-*-boundary` 잠정값을 그대로 싣는다 (E-74) — v2.5 전에는
 * null이면 축을 그리지 않는다는 계약이었다.
 */
public record SatisfactionMapResponse(
        String analysisYearMonth,
        AxisXView axisX,
        AxisYView axisY,
        BoundariesView boundaries,
        List<MapPointView> points
) {

    /** @param monthlyBudget 지출 부담의 분모. 설정 전이면 null이고 그때는 모든 burdenRatio가 null이다 */
    public record AxisXView(String label, String formula, Integer monthlyBudget) {
    }

    public record AxisYView(String label, List<Integer> range) {
    }

    public record BoundariesView(Double x, Double y) {
    }
}
