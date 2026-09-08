package kr.sottaejap.server.transaction.dto;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.domain.Transaction;

import java.time.OffsetDateTime;

/**
 * AI `SpringClient.get_transactions`가 받는 거래 한 건 (05 §3). 필드와 순서는 문서 표와 같다.
 *
 * <p>{@code occurredAt}은 한국 시간 오프셋(+09:00)으로 내보낸다. TIMESTAMPTZ를 읽으면 Hibernate가
 * UTC로 정규화해 {@code ...T10:05:00Z}가 나오는데, 05 §0 계약은 {@code +09:00} 표기이고
 * AI가 "밤 11시"처럼 시각을 그대로 문장에 쓰기 때문이다.
 */
public record TransactionAiView(Long id,
                                OffsetDateTime occurredAt,
                                String merchant,
                                int amount,
                                String category,
                                TimeSlot timeSlot,
                                Long behaviorId) {

    public static TransactionAiView from(Transaction transaction) {
        return new TransactionAiView(
                transaction.getId(),
                transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE).toOffsetDateTime(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getTimeSlot(),
                transaction.getBehaviorId());
    }
}
