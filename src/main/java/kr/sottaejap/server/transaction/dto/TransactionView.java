package kr.sottaejap.server.transaction.dto;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.transaction.domain.Transaction;

import java.time.OffsetDateTime;

/**
 * 외부 목록의 거래 한 건 (05 §2 `GET /transactions` · E-93). 회고 요약 {@code retrospectId} · {@code satisfaction}을
 * 실어 거래 화면과 회고 이력 탭이 한 API를 쓴다 — 회고가 없으면 둘 다 null이다.
 *
 * <p>내부 AI용 {@link TransactionAiView}와 공유하지 않는다 — 그쪽은 05 §3 계약에 고정돼 있어 화면 요구로
 * 필드가 늘면 AI 계약이 같이 흔들린다.
 */
public record TransactionView(Long id,
                              OffsetDateTime occurredAt,
                              String merchant,
                              int amount,
                              String category,
                              TimeSlot timeSlot,
                              Long retrospectId,
                              Satisfaction satisfaction) {

    /** {@code retrospect}는 null일 수 있다. 시각은 05 §0대로 +09:00으로 되돌린다 ({@link TransactionAiView}와 같은 이유). */
    public static TransactionView of(Transaction transaction, Retrospect retrospect) {
        return new TransactionView(
                transaction.getId(),
                transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE).toOffsetDateTime(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getTimeSlot(),
                retrospect == null ? null : retrospect.getId(),
                retrospect == null ? null : retrospect.getSatisfaction());
    }
}
