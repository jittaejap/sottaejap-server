package kr.sottaejap.server.rules.verdict;

/**
 * 분석 기준월 집계 (04 §3). 지도 가로축은 monthlyTotalAmount, 절감액은 avgAmount — 혼동 금지.
 *
 * @param monthlyTotalAmount 기준월 합계 (거래 없으면 0)
 * @param txCount            기준월 거래 건수
 * @param avgAmount          monthlyTotalAmount ÷ txCount (정수 나눗셈), txCount 0이면 null
 * @param burdenRatio        monthlyTotalAmount ÷ monthlyBudget, 예산이 null·0이면 null
 */
public record MonthlyBurden(int monthlyTotalAmount, int txCount, Integer avgAmount, Double burdenRatio) {
}
