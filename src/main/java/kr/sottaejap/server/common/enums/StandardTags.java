package kr.sottaejap.server.common.enums;

import java.util.List;

/**
 * 목적 7종 · 동행인 6종 고정 선택지 (결정로그 §3). 이 밖의 문자열은 저장하지 않고 400 INVALID_TAG로 거부한다 (E-20).
 */
public final class StandardTags {

    public static final List<String> PURPOSES = List.of("식사", "만남·사교", "휴식·취미", "필수품", "자기계발", "충동", "기타");
    public static final List<String> COMPANIONS = List.of("혼자", "친구", "가족", "연인", "동료", "기타");

    private StandardTags() {
    }

    public static boolean isPurpose(String value) {
        return PURPOSES.contains(value);
    }

    public static boolean isCompanion(String value) {
        return COMPANIONS.contains(value);
    }
}
