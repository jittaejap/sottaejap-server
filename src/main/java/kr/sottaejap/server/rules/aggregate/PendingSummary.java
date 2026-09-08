package kr.sottaejap.server.rules.aggregate;

/**
 * 보류 묶음 집계 (E-73). verdict가 null이라 {@link VerdictSummary} 어느 행에도 들어갈 수 없어 따로 낸다.
 * AI에게는 보내지 않는다 (E-75) — 판정이 없는 금액을 문장에 쓰게 할 이유가 없다.
 */
public record PendingSummary(int clusterCount, int monthlyTotalAmount, Double share) {
}
