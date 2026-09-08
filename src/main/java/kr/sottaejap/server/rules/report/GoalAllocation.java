package kr.sottaejap.server.rules.report;

/**
 * 목표 하나에 더할 실적 (E-94 ④). {@code amount}는 항상 1 이상이다 — 0원 배분은 목록에 넣지 않는다.
 */
public record GoalAllocation(long goalId, int amount) {
}
