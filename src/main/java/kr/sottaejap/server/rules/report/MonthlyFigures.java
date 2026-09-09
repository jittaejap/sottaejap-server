package kr.sottaejap.server.rules.report;

/**
 * 한 달의 집계 (04 §3 MonthlySnapshot). 거래가 없는 달은 전부 0이다 — "없음"은 부르는 쪽이 따로 가른다.
 *
 * @param totalSpending    그 달 거래 금액 합
 * @param unsatisfiedCount 그 달 거래의 회고 중 LOW 건수
 * @param repeatCount      유효 묶음 ∧ RESOLVED ∧ ADJUST 묶음의 그 달 거래 건수 합
 */
public record MonthlyFigures(int totalSpending, int unsatisfiedCount, int repeatCount) {
}
