package kr.sottaejap.server.suggestion.dto;

import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.SuggestionStatus;

/**
 * 제안 한 건 (05 §2 #15 · §3 내부 AI). AI는 {@code id}를 `state.suggestion_ids`와 대조해 고르므로
 * 이 값은 {@code suggestions} PK다 (E-81).
 *
 * <p>묶음 수치를 같이 싣는다 — AI도 화면도 "왜 이걸 줄이자는지"를 이 안의 값만으로 설명해야 한다 (NFR-02).
 */
public record SuggestionView(
        Long id,
        Long behaviorId,
        String behaviorName,
        int monthlyTotalAmount,
        Integer avgAmount,
        int txCount,
        Double adjustedSatisfaction,
        Quadrant quadrant,
        Integer adjustCount,
        Integer expectedSaving,
        Long goalId,
        SuggestionStatus status,
        String reason
) {
}
