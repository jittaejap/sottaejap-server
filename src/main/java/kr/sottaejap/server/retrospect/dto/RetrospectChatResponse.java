package kr.sottaejap.server.retrospect.dto;

import kr.sottaejap.server.common.enums.ReflectionStep;

import java.util.List;

/**
 * POST /retrospects/chat 응답 (05 §2 · E-63).
 *
 * @param step            서버가 계산한 다음 단계 — uncertainFields 순서(satisfaction → purpose → companion → repeatIntent), 비면 CONFIRM
 * @param reflection      AI 후보값, 표준 태그 밖은 null (E-20)
 * @param uncertainFields AI의 uncertain_fields를 camelCase로 (repeat_intention → repeatIntent)
 * @param fallback        AI 템플릿 응답이면 true (E-38) — 클라이언트는 S11 배너
 */
public record RetrospectChatResponse(
        String reply,
        ReflectionStep step,
        ReflectionDraft reflection,
        boolean needsClarification,
        List<String> uncertainFields,
        boolean fallback
) {
}
