package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.domain.Retrospect;

/** 내부 AI `get_reflections` 항목 (05 §3) — `{id, transactionId, satisfaction, purpose, companion, repeatIntent, status}`. */
public record ReflectionView(
        Long id,
        Long transactionId,
        Satisfaction satisfaction,
        String purpose,
        String companion,
        Boolean repeatIntent,
        RetrospectStatus status
) {

    public static ReflectionView from(Retrospect retrospect) {
        return new ReflectionView(
                retrospect.getId(),
                retrospect.getTransactionId(),
                retrospect.getSatisfaction(),
                retrospect.getPurpose(),
                retrospect.getCompanion(),
                retrospect.getRepeatIntent(),
                retrospect.getStatus());
    }
}
