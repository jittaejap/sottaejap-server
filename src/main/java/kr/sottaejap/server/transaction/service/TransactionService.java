package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface TransactionService {

    /**
     * CSV · XLSX를 파싱해 저장한다. <b>묶음 재계산은 여기서 하지 않는다</b> — 업로드를 커밋한 뒤 도는 것이
     * 계약이고 그 배치 자리는 {@link TransactionUploadFacade}다 (E-95). 여기에 재계산을 주입하면 순환
     * 참조로 기동이 죽는다.
     */
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
