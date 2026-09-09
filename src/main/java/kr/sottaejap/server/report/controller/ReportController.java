package kr.sottaejap.server.report.controller;

import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** 05 §2 #17 — 월간 리포트 (FR-08-06 · FR-08-07). */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** `?yearMonth=YYYY-MM`은 선택이다. 형식이 틀리면 변환 실패로 400 `INVALID_INPUT`, 미래 달도 400이다. */
    @GetMapping("/monthly")
    public ApiResponse<MonthlyReportResponse> monthly(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @RequestParam(required = false) YearMonth yearMonth) {
        return ApiResponse.success(reportService.monthly(user.userId(), yearMonth));
    }
}
