package kr.sottaejap.server.onboarding.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * `POST /onboarding/start` 본문 (05 §2 · E-92).
 *
 * <p>기간은 업로드 응답(`POST /transactions/upload`)의 `periodFrom`·`periodTo`를 그대로 싣는다.
 * {@code sampleSize}의 상한은 서비스가 100으로 자른다 — 400으로 거절하지 않는 것은 후보 API와 같은 규칙이다.
 * 세 값 모두 필수라 {@code Integer}·{@code LocalDate}에 {@code @NotNull}을 건다. {@code int}로 두면
 * 본문에서 빠졌을 때 0이 유효값으로 통과한다.
 */
public record OnboardingStartRequest(
        @NotNull @Min(1) Integer sampleSize,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo
) {
}
