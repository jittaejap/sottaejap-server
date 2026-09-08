package kr.sottaejap.server.chat.repository;

import kr.sottaejap.server.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최신 것부터 읽는다. AI에 실을 때는 시간 순서로 뒤집는다. */
    List<ChatMessage> findByUserIdAndTransactionIdOrderByCreatedAtDesc(long userId, Long transactionId,
                                                                      Pageable pageable);

    /** 거래에 매이지 않는 대화 — 금융 Q&A. 파생 질의는 null을 `= null`로 만들어 못 찾으므로 IsNull을 쓴다. */
    List<ChatMessage> findByUserIdAndTransactionIdIsNullOrderByCreatedAtDesc(long userId, Pageable pageable);
}
