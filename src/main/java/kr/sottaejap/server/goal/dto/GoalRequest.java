package kr.sottaejap.server.goal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * `POST /goals` · `PUT /goals/{id}` 본문 (05 §2 · E-83).
 *
 * <p>{@code name}·{@code targetAmount}는 생성·수정 모두 필수다 — 05 §2의 규칙 표가 두 메서드에 같이 걸린다.
 * {@code targetAmount}에 {@code @Positive}만 두면 null이 유효값으로 통과해 {@code Goal.create}에서
 * {@code int}로 언박싱되며 500이 된다. 생략할 수 있는 것은 {@code targetDate}와 {@code currentAmount}뿐이다.
 *
 * <p><b>{@code targetDate}에 검증 어노테이션을 붙이지 않는다.</b> {@code @NotNull}이면 이 필드를 아직
 * 보내지 않는 지금의 온보딩 1단계가 전부 400이 되고, {@code @Future}면 예정일이 지난 목표는 이름조차
 * 고칠 수 없다 — {@code PUT}이 전체 교체라 화면이 같은 날짜를 되보낸다. 형식 오류는 Jackson이 먼저
 * 실패하고 {@code GlobalExceptionHandler}가 {@code HttpMessageNotReadableException}을
 * 400 {@code INVALID_INPUT}으로 바꾼다 — 새로 쓸 코드가 없다.
 */
public record GoalRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @Positive Integer targetAmount,
        LocalDate targetDate,
        @Min(0) Integer currentAmount
) {
}
