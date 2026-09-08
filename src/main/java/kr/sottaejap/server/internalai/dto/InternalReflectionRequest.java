package kr.sottaejap.server.internalai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;

/**
 * AI `save_reflection` 본문 (05 §3). AI는 snake_case로 보내고 여기서 받는다. 사용자가 확인한 값만 온다 (E-20).
 * source는 오지 않으므로 CANDIDATE로 저장한다 (E-66).
 *
 * <p>{@code transaction_id}가 빠지면 400 INVALID_INPUT이다 — 검증 없이 넘기면 조회가 빈 결과를 내 404 NOT_FOUND가
 * 나가고, 부르는 쪽에서 "필드가 빠졌다"를 "그런 거래가 없다"로 읽게 된다.
 */
public record InternalReflectionRequest(
        @JsonProperty("transaction_id") @NotNull Long transactionId,
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
