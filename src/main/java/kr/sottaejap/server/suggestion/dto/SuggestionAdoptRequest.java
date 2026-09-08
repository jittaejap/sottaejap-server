package kr.sottaejap.server.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * `POST /suggestions/{id}/adopt` 본문 (05 §2 · E-82).
 *
 * <p>상한(거래 건수)은 묶음마다 달라 애노테이션으로 걸 수 없다 — 서비스가 {@code SavingRule}로 검사한다.
 *
 * @param goalId 배분할 목표. 생략하면 <b>기존 연결을 그대로 둔다</b> — 첫 채택이면 붙이지 않고 채택만 하고,
 *               이미 채택한 제안의 횟수만 고칠 때는 붙어 있던 목표가 유지된다
 */
public record SuggestionAdoptRequest(@NotNull @Positive Integer adjustCount, Long goalId) {
}
