package kr.sottaejap.server.internalai;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.sottaejap.server.common.enums.Satisfaction;

/**
 * AI `save_reflection` 본문 (05 §3). AI는 snake_case로 보내고 여기서 받는다. 사용자가 확인한 값만 온다 (E-20).
 */
public record InternalReflectionRequest(
        @JsonProperty("transaction_id") Long transactionId,
        Satisfaction satisfaction,
        String purpose,
        String companion,
        @JsonProperty("repeat_intention") Boolean repeatIntention
) {
}
