package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.TimeSlot;

import java.util.Map;

/**
 * 묶음 이름 템플릿 (⑤ · E-64). AI가 503이거나 빈 응답을 주면 이 이름을 쓴다.
 *
 * <p>{@code "{시간대 라벨} {카테고리}"}, 시간대 자리가 비어 있으면 {@code "{카테고리} {목적}"},
 * 둘 다 없으면 카테고리뿐이다. 결과는 12자를 넘지 않는다 (FR-05-05).
 */
public final class ClusterNameTemplate {

    /** 묶음 이름 표기 규격 — 규칙 값이 아니라 05 · FR-05-05의 화면 문구 길이다. */
    public static final int MAX_NAME_LENGTH = 12;

    private static final Map<String, String> TIME_SLOT_LABELS = Map.of(
            TimeSlot.MORNING.name(), "아침",
            TimeSlot.DAY.name(), "낮",
            TimeSlot.EVENING.name(), "저녁",
            TimeSlot.NIGHT.name(), "심야");

    private ClusterNameTemplate() {
    }

    /**
     * 묶음 키 {@code 카테고리|시간대|목적|동행인}에서 이름을 만든다 (E-59의 네 자리 키).
     */
    public static String nameFor(String clusterKey) {
        String[] parts = clusterKey.split("\\|", -1);
        String category = parts[0];
        String timeSlotLabel = TIME_SLOT_LABELS.get(parts[1]);
        String purpose = parts[2];

        if (timeSlotLabel != null) {
            return truncate(timeSlotLabel + " " + category);
        }
        if (!purpose.isBlank()) {
            return truncate(category + " " + purpose);
        }
        return truncate(category);
    }

    /** AI가 지은 이름에도 같은 길이 규격을 적용한다 (FR-05-05). */
    public static String truncate(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
