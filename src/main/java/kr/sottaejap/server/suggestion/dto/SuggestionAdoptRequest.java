package kr.sottaejap.server.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * `POST /suggestions/{id}/adopt` 본문 (05 §2 · E-82).
 *
 * <p>상한(거래 건수)은 묶음마다 달라 애노테이션으로 걸 수 없다 — 서비스가 {@code SavingRule}로 검사한다.
 *
 * @param goalId 배분할 목표. 생략하면 목표에 붙이지 않고 채택만 한다
 */
public record SuggestionAdoptRequest(@NotNull @Positive Integer adjustCount, Long goalId) {
}
