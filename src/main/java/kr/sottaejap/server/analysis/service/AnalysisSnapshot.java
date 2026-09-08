package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 번의 DB 왕복으로 읽어 둔 분석 입력. 이걸 먼저 만들어 두는 이유는 그 다음 단계가 AI를 부르기 때문이다 —
 * AI 왕복(최대 15초) 동안 DB 트랜잭션을 열어 두지 않는다.
 *
 * @param clusters 유효 묶음만 (E-72)
 */
public record AnalysisSnapshot(YearMonth analysisYearMonth, Integer monthlyBudget, List<ClusterSnapshot> clusters) {

    public String analysisYearMonthText() {
        return analysisYearMonth == null ? null : analysisYearMonth.toString();
    }
}
