package kr.sottaejap.server.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI POST /chat의 {@code recent_messages} 항목 (05 §3). 클라이언트가 {@code recentMessages}로 보낸 것을 그대로 싣는
 * 채널(REFLECTION · ANALYSIS)은 요청 DTO가 {@code List<@Valid ChatMessage>}로 이 제약을 검사한다 (E-109) — 규격 밖
 * 항목을 넘기면 AI 스키마의 422를 {@code AiClient}가 503 {@code LLM_UNAVAILABLE}로 바꿔 입력 오류가 장애처럼 보인다 (#56).
 * {@code FINANCE_QA}처럼 서버가 DB에서 읽어 만드는 항목에는 검사가 걸리지 않는다.
 *
 * @param role    "user" 또는 "assistant" — AI 스키마와 같다
 * @param content 공백 아닌 1~500자. 상한은 {@code message}와 같은 값, 같은 이유(프롬프트 예산 — 6건이 함께 실린다)
 */
public record ChatMessage(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content
) {

    public static final int MAX_CONTENT_LENGTH = 500;
}
