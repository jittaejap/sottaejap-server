package kr.sottaejap.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /chat/finance 요청 (05 #24 · FR-12). 05 §2에 상세 명세가 없어 §3의 `FINANCE_QA` 규격에서 역산했다.
 *
 * <p>보낼 것은 질문 하나뿐이다. `task_context.state`는 빈 객체이고 맥락은 최근 대화가 담당한다 (E-47).
 *
 * @param message 1~500자 (05 §2 v2.10). 상한은 프롬프트 예산에서 나온 값이다 — 같은 요청에 최근 대화
 *                6건이 함께 실리므로, 질문 하나가 길어지면 되물음의 맥락이 먼저 잘린다
 */
public record FinanceChatRequest(@NotBlank @Size(max = MAX_MESSAGE_LENGTH) String message) {

    static final int MAX_MESSAGE_LENGTH = 500;
}
