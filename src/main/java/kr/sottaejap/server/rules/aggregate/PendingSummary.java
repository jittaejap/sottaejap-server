package kr.sottaejap.server.rules.aggregate;

/**
 * 보류 묶음 집계 (E-73). verdict가 null이라 {@link VerdictSummary} 어느 행에도 들어갈 수 없어 따로 낸다.
 *
 * <p>AI로 가는 길은 둘이고 이 집계가 실리는 곳은 하나다. {@code ANALYSIS_NARRATE}의 {@code task_context.state}에는
 * <b>싣지 않는다</b> (E-75) — 판정이 없는 금액을 문장에 쓰게 할 이유가 없다. 내부 AI Tool 응답
 * ({@code InternalAnalysisResponse})에는 <b>싣는다</b> — ai가 유효 묶음 수를 셀 때 {@code clusterCount}를 읽는다 (ai #50).
 * "AI에게는 보내지 않는다"로만 읽으면 ai가 {@code pending}을 못 본다는 잘못된 결론이 나온다 (server PR #53 리뷰).
 */
public record PendingSummary(int clusterCount, int monthlyTotalAmount, Double share) {
}
