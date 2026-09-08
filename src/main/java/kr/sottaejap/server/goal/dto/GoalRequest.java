package kr.sottaejap.server.goal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * `POST /goals` · `PUT /goals/{id}` 본문 (05 §2 · E-83).
 *
 * <p>{@code name}·{@code targetAmount}는 생성·수정 모두 필수다 — 05 §2의 규칙 표가 두 메서드에 같이 걸린다.
 * {@code targetAmount}에 {@code @Positive}만 두면 null이 유효값으로 통과해 {@code Goal.create}에서
 * {@code int}로 언박싱되며 500이 된다. 생략할 수 있는 것은 {@code currentAmount}뿐이고, 없으면 0이다.
 */
public record GoalRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @Positive Integer targetAmount,
        @Min(0) Integer currentAmount
) {
}
