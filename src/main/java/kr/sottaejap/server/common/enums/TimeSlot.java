package kr.sottaejap.server.common.enums;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 시간대 4구간 (E-50 · 04 §1 · 05 §0 v1.9). MORNING 05~11 · DAY 11~17 · EVENING 17~22 · NIGHT 22~05.
 *
 * <p>AFTERNOON은 폐기됐다 (06 R13). 경계는 시작 시각을 포함하고 끝 시각을 제외한다 — 11:00은 DAY다.
 */
public enum TimeSlot {

    MORNING,
    DAY,
    EVENING,
    NIGHT;

    /** 거래는 국내 카드 내역이므로 한국 시간 기준으로 구간을 나눈다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 거래 시각을 시간대 구간으로 분류한다. 규칙 엔진이 아니라 파서 경계이므로 여기 둔다 (06 R13).
     */
    public static TimeSlot from(OffsetDateTime occurredAt) {
        int hour = occurredAt.atZoneSameInstant(ZONE).getHour();
        if (hour >= 5 && hour < 11) {
            return MORNING;
        }
        if (hour >= 11 && hour < 17) {
            return DAY;
        }
        if (hour >= 17 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }
}
