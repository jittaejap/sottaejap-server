package kr.sottaejap.server.common.enums;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 목적 7종 · 동행인 6종 고정 선택지 (결정로그 §3). 이 밖의 문자열은 저장하지 않고 400 INVALID_TAG로 거부한다 (E-20).
 *
 * <p>가운뎃점 둘레의 공백처럼 <b>표기만 다른 값은 같은 태그로 본다.</b> 화면은 읽기 좋으라고 {@code "휴식 · 취미"}로
 * 보여주고 정본은 {@code "휴식·취미"}라, 통째로 비교하면 목적 7종 중 둘이 저장되지 않는다. 통과시키더라도
 * purpose는 묶음 키의 일부라 같은 태그가 두 묶음으로 갈라진다 — 그래서 받는 자리에서 정본 표기로 되돌린다.
 */
public final class StandardTags {

    public static final List<String> PURPOSES = List.of("식사", "만남·사교", "휴식·취미", "필수품", "자기계발", "충동", "기타");
    public static final List<String> COMPANIONS = List.of("혼자", "친구", "가족", "연인", "동료", "기타");

    /** 표준 태그 13종에는 공백이 없다. 줄바꿈과 비분리 공백(U+00A0)까지 지운 뒤 목록과 맞춘다. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private StandardTags() {
    }

    /** 표준 태그면 정본 표기, 밖이면 null. null 입력은 미확정이므로 null 그대로다. */
    public static String normalizePurpose(String value) {
        return canonical(PURPOSES, value);
    }

    /** {@link #normalizePurpose}의 동행인 판. */
    public static String normalizeCompanion(String value) {
        return canonical(COMPANIONS, value);
    }

    /** 정본 표기를 돌려주되 표준 태그 밖이면 400 INVALID_TAG. null은 미확정이라 통과시킨다 (E-20). */
    public static String requirePurpose(String value) {
        return require(value, normalizePurpose(value));
    }

    /** {@link #requirePurpose}의 동행인 판. */
    public static String requireCompanion(String value) {
        return require(value, normalizeCompanion(value));
    }

    private static String canonical(List<String> tags, String value) {
        if (value == null) {
            return null;
        }
        String stripped = WHITESPACE.matcher(value).replaceAll("");
        return tags.contains(stripped) ? stripped : null;
    }

    private static String require(String value, String canonical) {
        if (value != null && canonical == null) {
            throw new BusinessException(CommonErrorCode.INVALID_TAG);
        }
        return canonical;
    }
}
