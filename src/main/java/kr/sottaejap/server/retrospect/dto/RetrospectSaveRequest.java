package kr.sottaejap.server.retrospect.dto;

import jakarta.validation.constraints.NotNull;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;

/**
 * POST /retrospects 본문 (05 §2). purpose·companion은 표준 태그 또는 null — 밖이면 서비스가 400 INVALID_TAG (E-20).
 *
 * @param source 없으면 CANDIDATE (E-66 — 내부 AI 경로는 보내지 않는다)
 */
public record RetrospectSaveRequest(
        @NotNull Long transactionId,
        @NotNull Satisfaction satisfaction,
        String purpose,
        String companion,
        Boolean repeatIntent,
        RetrospectSource source
) {

    public RetrospectSource sourceOrDefault() {
        return source == null ? RetrospectSource.CANDIDATE : source;
    }
}
