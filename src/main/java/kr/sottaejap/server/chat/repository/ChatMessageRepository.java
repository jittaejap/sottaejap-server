package kr.sottaejap.server.chat.repository;

import kr.sottaejap.server.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 거래에 매이지 않는 대화 — 금융 Q&A. 파생 질의는 null을 `= null`로 만들어 못 찾으므로 IsNull을 쓴다.
     *
     * <p>최신 것부터 읽는다. AI에 실을 때는 시간 순서로 뒤집는다.
     *
     * <p>질문과 답변은 같은 created_at으로 저장되므로 id로 한 번 더 가른다. 시각만으로 정렬하면 같은 값의
     * 순서를 Postgres가 보장하지 않아 한 턴이 `assistant → user`로 뒤집혀 AI에 실릴 수 있고, 6건에서
     * 잘리는 자리도 매번 달라진다 (E-67 — 되물음이 그대로 이어져야 한다).
     */
    List<ChatMessage> findByUserIdAndTransactionIdIsNullOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);
}
