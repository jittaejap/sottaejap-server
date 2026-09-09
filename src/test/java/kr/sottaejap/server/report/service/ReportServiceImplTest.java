package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 어느 달을 볼지 정하고 미래 달을 막는다 (05 §2 #17 · E-94 ⑤). */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final long USER_ID = 1L;
    /** 2026-09-09 KST. 현재 연월은 2026-09이다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final YearMonth CURRENT = YearMonth.of(2026, 9);

    @Mock
    private TransactionService transactionService;
    @Mock
    private MonthlySnapshotService monthlySnapshotService;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(transactionService, monthlySnapshotService, CLOCK);
    }

    @Test
    void 달을_주면_그_달과_현재_연월을_넘긴다() {
        MonthlyReportResponse expected = response("2026-08");
        when(monthlySnapshotService.monthly(USER_ID, YearMonth.of(2026, 8), CURRENT)).thenReturn(expected);

        assertEquals(expected, service.monthly(USER_ID, YearMonth.of(2026, 8)));
        verifyNoInteractions(transactionService);
    }

    @Test
    void 달을_안_주면_분석_기준월이다() {
        when(transactionService.analysisYearMonth(USER_ID)).thenReturn(YearMonth.of(2026, 7));
        when(monthlySnapshotService.monthly(USER_ID, YearMonth.of(2026, 7), CURRENT)).thenReturn(response("2026-07"));

        assertEquals("2026-07", service.monthly(USER_ID, null).yearMonth());
    }

    @Test
    void 거래가_없어_기준월도_없으면_현재_연월이다() {
        when(transactionService.analysisYearMonth(USER_ID)).thenReturn(null);
        when(monthlySnapshotService.monthly(USER_ID, CURRENT, CURRENT)).thenReturn(response("2026-09"));

        assertEquals("2026-09", service.monthly(USER_ID, null).yearMonth());
    }

    @Test
    void 미래_달은_400이다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.monthly(USER_ID, YearMonth.of(2026, 10)));

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(monthlySnapshotService);
    }

    @Test
    void 현재_연월은_미래가_아니다() {
        when(monthlySnapshotService.monthly(eq(USER_ID), eq(CURRENT), any())).thenReturn(response("2026-09"));

        service.monthly(USER_ID, CURRENT);

        verify(monthlySnapshotService).monthly(USER_ID, CURRENT, CURRENT);
    }

    private static MonthlyReportResponse response(String yearMonth) {
        return new MonthlyReportResponse(yearMonth, false, 0, null, null, 0, 0, null, List.of());
    }
}
