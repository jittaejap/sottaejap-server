package kr.sottaejap.server.internalai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;

/**
 * AI `save_reflection` 본문 (05 §3). AI는 snake_case로 보내고 여기서 받는다. 사용자가 확인한 값만 온다 (E-20).
 * source는 오지 않으므로 CANDIDATE로 저장한다 (E-66).
 */
public record InternalReflectionRequest(
        @JsonProperty("transaction_id") Long transactionId,
        Satisfaction satisfaction,
        String purpose,
        String companion,
        @JsonProperty("repeat_intention") Boolean repeatIntention
) {

    /** 외부 POST /retrospects와 같은 검증·응답을 태우기 위해 변환한다 (E-66). satisfaction이 없으면 UNKNOWN. */
    public RetrospectSaveRequest toSaveRequest() {
        return new RetrospectSaveRequest(transactionId,
                satisfaction == null ? Satisfaction.UNKNOWN : satisfaction,
                purpose, companion, repeatIntention, null);
    }
}
