package kr.sottaejap.server.report.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.report.dto.MonthlyReportResponse;
import kr.sottaejap.server.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;

/**
 * 리포트 API의 입구 — 어느 달인지 정하고 미래 달을 막는다 (05 §2 #17 · E-94 ⑤). 계산과 확정은
 * {@link MonthlySnapshotService}가 한다. "현재 연월"은 여기서 {@link Clock}으로 한 번만 읽어 넘긴다.
 *
 * <p>거래가 하나도 없어 분석 기준월이 null인 사용자는 현재 연월을 본다 — 데이터 없는 달도 200이고(E-94 ⑤),
 * 현재 연월은 저장하지 않으므로 부작용이 없다.
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TransactionService transactionService;
    private final MonthlySnapshotService monthlySnapshotService;
    private final Clock clock;

    @Override
    public MonthlyReportResponse monthly(long userId, YearMonth yearMonth) {
        YearMonth currentMonth = YearMonth.now(clock);
        YearMonth month = yearMonth != null ? yearMonth : defaultMonth(userId, currentMonth);
        if (month.isAfter(currentMonth)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return monthlySnapshotService.monthly(userId, month, currentMonth);
    }

    private YearMonth defaultMonth(long userId, YearMonth currentMonth) {
        YearMonth analysisYearMonth = transactionService.analysisYearMonth(userId);
        return analysisYearMonth == null ? currentMonth : analysisYearMonth;
    }
}
