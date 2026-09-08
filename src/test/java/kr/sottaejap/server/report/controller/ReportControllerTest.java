package kr.sottaejap.server.report.controller;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.report.dto.MonthlyReportResponse.GoalAllocationView;
import kr.sottaejap.server.report.service.ReportService;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** `GET /reports/monthly` HTTP 계약 (05 §2 #17). */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private ReportService reportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 응답은_05의_필드를_그대로_싣는다() throws Exception {
        when(reportService.monthly(USER_ID, YearMonth.of(2026, 8))).thenReturn(new MonthlyReportResponse(
                "2026-08", true, 412_000, 448_000, 36_000, 4, 3, 5, List.of(new GoalAllocationView(3L, 36_000))));

        mockMvc.perform(get("/reports/monthly").param("yearMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.finalized").value(true))
                .andExpect(jsonPath("$.data.totalSpending").value(412_000))
                .andExpect(jsonPath("$.data.previousTotalSpending").value(448_000))
                .andExpect(jsonPath("$.data.savedAmount").value(36_000))
                .andExpect(jsonPath("$.data.unsatisfiedCount").value(4))
                .andExpect(jsonPath("$.data.repeatCount").value(3))
                .andExpect(jsonPath("$.data.previousRepeatCount").value(5))
                .andExpect(jsonPath("$.data.goalAllocations[0].goalId").value(3))
                .andExpect(jsonPath("$.data.goalAllocations[0].amount").value(36_000));
    }

    @Test
    void 달을_안_주면_null로_넘긴다() throws Exception {
        when(reportService.monthly(USER_ID, null)).thenReturn(new MonthlyReportResponse(
                "2026-09", false, 0, null, null, 0, 0, null, List.of()));

        mockMvc.perform(get("/reports/monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalized").value(false))
                .andExpect(jsonPath("$.data.savedAmount").doesNotExist())
                .andExpect(jsonPath("$.data.goalAllocations").isEmpty());

        verify(reportService).monthly(USER_ID, null);
    }

    @Test
    void 형식이_틀린_달은_400이다() throws Exception {
        mockMvc.perform(get("/reports/monthly").param("yearMonth", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/reports/monthly").param("yearMonth", "202608"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void 미래_달은_400이다() throws Exception {
        when(reportService.monthly(USER_ID, YearMonth.of(2099, 1)))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/reports/monthly").param("yearMonth", "2099-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
