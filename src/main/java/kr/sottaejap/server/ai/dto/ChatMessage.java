package kr.sottaejap.server.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * AI POST /chat의 {@code recent_messages} 항목 (05 §3). 클라이언트가 {@code recentMessages}로 보낸 것을 그대로 싣는
 * 채널(REFLECTION · ANALYSIS)은 요청 DTO가 {@code List<@NotNull @Valid ChatMessage>}로 이 제약을 검사한다 (E-109) — 규격 밖
 * 항목을 넘기면 AI 스키마의 422를 {@code AiClient}가 503 {@code LLM_UNAVAILABLE}로 바꿔 입력 오류가 장애처럼 보인다 (#56).
 * {@code FINANCE_QA}처럼 서버가 DB에서 읽어 만드는 항목에는 검사가 걸리지 않는다.
 *
 * <p>{@code content} 상한은 role별이다 (E-110 · #61) — {@code user}는 {@code message}와 같은 500자(같은 이유, 프롬프트 예산),
 * {@code assistant}는 2,000자. assistant 항목은 AI {@code reply}를 client가 되돌려 보낸 것이라 500자로 두면 서버가 만든
 * 길이로 client가 400을 맞는다. 2,000자 이내는 ai가 보장한다 (ai #55). 길이는 <b>코드 포인트</b>로 센다 — 01 E-110 · 05 §3 ·
 * ai #55와 같은 단위이고, {@code ClusterNameTemplate.truncate}(12자 · 이슈 #20)의 선례를 따른다. {@code message}의
 * {@code @Size}(UTF-16)와는 단위가 다르지만 그쪽은 서버 자체 입력 상한이지 AI와 맺은 약속이 아니다.
 *
 * @param role    "user" 또는 "assistant" — AI 스키마와 같다
 * @param content 공백 아닌 1자 이상, 상한은 role별 ({@link ContentWithinRoleLimit})
 */
@ContentWithinRoleLimit
public record ChatMessage(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank String content
) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public static final int MAX_USER_CONTENT_LENGTH = 500;
    public static final int MAX_ASSISTANT_CONTENT_LENGTH = 2000;
}
