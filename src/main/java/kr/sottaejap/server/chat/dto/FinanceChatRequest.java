package kr.sottaejap.server.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /chat/finance 요청 (05 #24 · FR-12). 05 §2에 상세 명세가 없어 §3의 `FINANCE_QA` 규격에서 역산했다.
 *
 * <p>보낼 것은 질문 하나뿐이다. `task_context.state`는 빈 객체이고 맥락은 최근 대화가 담당한다 (E-47).
 */
public record FinanceChatRequest(@NotBlank String message) {
}
