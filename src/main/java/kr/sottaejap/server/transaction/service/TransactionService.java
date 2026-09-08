package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface TransactionService {

    TransactionUploadResponse upload(long userId, MultipartFile file);

    /** AI `SpringClient.get_transactions`가 쓰는 조회 (05 §3). */
    List<TransactionAiView> findForAi(long userId, LocalDate from, LocalDate to, String category, Integer size);
}
