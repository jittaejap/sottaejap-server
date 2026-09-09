package kr.sottaejap.server.rules.report;

/**
 * 배분 대상 목표 — ADOPTED 제안이 붙은 목표와 그 목표의 {@code expectedSaving} 합 (E-83 · E-94).
 *
 * @param goalId        목표 id
 * @param expectedSaving 이 목표에 붙은 ADOPTED 제안의 expectedSaving 합. 배분 비율의 가중치다
 */
public record GoalSaving(long goalId, int expectedSaving) {
}
