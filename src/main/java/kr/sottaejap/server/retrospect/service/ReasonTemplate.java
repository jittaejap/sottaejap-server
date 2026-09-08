package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.ReasonCode;

/**
 * 후보 선정 사유 문장 (E-62). reasonCode당 한 문장을 Spring이 만든다 — 후보 N건마다 LLM 왕복을 만들지 않는다.
 *
 * <p>문장은 AI 레포 `app/ai/fallback.py`의 `_REASON_SENTENCES`와 글자 단위로 같다 (E-62). 같은 코드인데
 * 목록 화면과 채팅 폴백의 문장이 다르면 사용자에게는 다른 이유로 읽힌다. AI가 다시 쓴 문장은
 * `POST /retrospects/chat`의 INTRO에서만 받는다 (FR-04-10 · 11).
 */
public final class ReasonTemplate {

    private ReasonTemplate() {
    }

    /** switch 식이라 ReasonCode에 값을 더하면 여기서 컴파일이 깨진다 — 문장 없는 코드를 만들지 않는다. */
    public static String reasonFor(ReasonCode code) {
        return switch (code) {
            case TIMESLOT_OUTLIER -> "평소와 다른 시간대의 소비였어요.";
            case THRESHOLD_EXCEEDED -> "설정하신 기준 금액을 넘은 소비였어요.";
            case REPEATED_LOW_SATISFACTION -> "비슷한 소비에서 만족도가 낮았던 적이 있어요.";
            case ONBOARDING_SAMPLE -> "최근 소비 중에서 함께 돌아볼 거래로 골랐어요.";
            case MANUAL_PICK -> "직접 추가하신 거래예요.";
        };
    }
}
