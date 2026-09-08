package kr.sottaejap.server.report.service;

import kr.sottaejap.server.report.dto.MonthlyReportResponse;

import java.time.YearMonth;

/**
 * 월간 리포트의 계산 · 확정 (E-94 ③ ④). 시점 규칙은 여기가 갖고, 산식은 {@code rules/report}가 갖는다.
 *
 * <p>{@code currentMonth}는 부르는 쪽이 {@code Clock}으로 넘긴다 — 여기서도 규칙에서도 now()를 부르지 않는다.
 * {@code month}가 미래가 아닌 것은 부르는 쪽이 보장한다.
 */
public interface MonthlySnapshotService {

    /**
     * {@code month} < {@code currentMonth}면 스냅샷이 없을 때 계산해 저장하고 목표 실적을 배분한 뒤 고정한다.
     * {@code month} = {@code currentMonth}면 계산만 하고 저장하지 않는다.
     */
    MonthlyReportResponse monthly(long userId, YearMonth month, YearMonth currentMonth);
}
