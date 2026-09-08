package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.domain.Transaction;

import java.time.OffsetDateTime;

/**
 * GET /retrospects/candidates 항목 (05 §2). occurredAt은 +09:00으로 재투영한다 — TIMESTAMPTZ는 UTC로 읽힌다.
 *
 * @param reason Spring 템플릿 문장 (E-62)
 */
public record CandidateView(
        Long transactionId,
        OffsetDateTime occurredAt,
        String merchant,
        int amount,
        String category,
        TimeSlot timeSlot,
        ReasonCode reasonCode,
        String reason
) {

    public static CandidateView from(Transaction transaction, ReasonCode reasonCode, String reason) {
        return new CandidateView(
                transaction.getId(),
                transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE).toOffsetDateTime(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getTimeSlot(),
                reasonCode,
                reason);
    }
}
