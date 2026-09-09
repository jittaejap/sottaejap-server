package kr.sottaejap.server.rules.report;

import kr.sottaejap.server.common.enums.Satisfaction;

import java.time.YearMonth;

/**
 * 월간 산식 입력 한 건 (E-94). 회고가 없는 거래도 들어온다 — {@code totalSpending}은 전체 거래 합이다.
 *
 * @param amount          거래 금액
 * @param occurredMonth   거래가 일어난 달(KST)
 * @param satisfaction    이 거래의 회고 만족도. 회고가 없으면 null
 * @param inAdjustCluster 유효 묶음(E-72) ∧ RESOLVED ∧ ADJUST 묶음(또는 그 자식 리프)에 배정된 거래인지 (E-73 정합)
 */
public record MonthlyTransaction(int amount, YearMonth occurredMonth, Satisfaction satisfaction, boolean inAdjustCluster) {
}
