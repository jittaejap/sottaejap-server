package kr.sottaejap.server.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * `PUT /users/me/settings` 본문 (05 §2 API 3 · E-77 · FR-01-03,04,06). 온보딩 2단계와 마이페이지가 같은 본문을 쓴다.
 *
 * <p>네 값 모두 선택이고 <b>null은 "그대로 두기"</b>다. 다만 넷이 전부 비면 거절한다 — 필드 이름을 잘못 보낸
 * 요청이 200으로 조용히 아무것도 안 하는 편보다, 400으로 드러나는 편이 고치기 쉽다.
 *
 * @param monthlyBudget       월 예산 — 지출 부담의 분모다 (FR-06-06). 이 값이 없으면 지도의 가로축이 서지 않는다
 * @param outlierThreshold    이상치 민감도 배수 (E-46) — 규칙 ④ TIMESLOT_OUTLIER에 쓴다
 * @param outlierBaseAmount   큰 금액 기준 금액, 원 단위 (E-115) — 규칙 ③ THRESHOLD_EXCEEDED에 쓴다. 배수와 다른 값이다
 * @param retrospectDelayDays 회고 알림까지 기다리는 날 수 (D+N). 상한 30은 입력 폼 가드이지 규칙 값이 아니다
 */
public record UserSettingsRequest(
        @Positive Integer monthlyBudget,
        @Positive Double outlierThreshold,
        @Positive Integer outlierBaseAmount,
        @Min(0) @Max(30) Integer retrospectDelayDays
) {

    @AssertTrue(message = "바꿀 값을 하나는 보내야 합니다")
    public boolean isNotEmpty() {
        return monthlyBudget != null || outlierThreshold != null
                || outlierBaseAmount != null || retrospectDelayDays != null;
    }
}
