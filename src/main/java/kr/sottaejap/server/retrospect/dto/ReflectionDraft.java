package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.Satisfaction;

/**
 * 대화 중 회고 값 (E-63). 요청에서는 사용자가 확인한 값, 응답에서는 AI 후보값(표준 태그 밖은 null).
 * 저장은 하지 않는다 — 저장은 POST /retrospects.
 */
public record ReflectionDraft(Satisfaction satisfaction, String purpose, String companion, Boolean repeatIntent) {

    public static ReflectionDraft empty() {
        return new ReflectionDraft(Satisfaction.UNKNOWN, null, null, null);
    }
}
