package kr.sottaejap.server.report.service;

import kr.sottaejap.server.report.dto.MonthlyReportResponse;

import java.time.YearMonth;

public interface ReportService {

    /**
     * {@code GET /reports/monthly} (05 §2 #17). {@code yearMonth}가 null이면 분석 기준월(E-60)이고,
     * 현재 KST 연월보다 뒤면 400 {@code INVALID_INPUT}이다.
     */
    MonthlyReportResponse monthly(long userId, YearMonth yearMonth);
}
