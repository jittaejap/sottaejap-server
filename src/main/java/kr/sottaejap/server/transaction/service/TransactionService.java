package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface TransactionService {

    TransactionUploadResponse upload(long userId, MultipartFile file);

    /** AI `SpringClient.get_transactions`가 쓰는 조회 (05 §3). */
    List<TransactionAiView> findForAi(long userId, LocalDate from, LocalDate to, String category, Integer size);

    /**
     * 분석 기준월 = 사용자의 최근 거래월(KST) (E-60). 거래가 없으면 null이다.
     *
     * <p>이 값의 산출지는 여기 하나다 (E-78). 묶음 행의 {@code analysisYearMonth}를 읽으면 비워진 묶음의
     * 옛 달이 섞여 나온다.
     */
    YearMonth analysisYearMonth(long userId);
}
