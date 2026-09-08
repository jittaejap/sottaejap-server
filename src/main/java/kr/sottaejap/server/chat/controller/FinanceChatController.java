package kr.sottaejap.server.chat.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.chat.dto.FinanceChatRequest;
import kr.sottaejap.server.chat.dto.FinanceChatResponse;
import kr.sottaejap.server.chat.service.FinanceChatService;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class FinanceChatController {

    private final FinanceChatService financeChatService;

    /** 금융 지식 Q&A (05 #24). 답변 근거는 AI의 금융 RAG가 찾는다 — Spring은 판단하지 않는다. */
    @PostMapping("/finance")
    public ApiResponse<FinanceChatResponse> finance(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestBody @Valid FinanceChatRequest request) {
        return ApiResponse.success(financeChatService.ask(user.userId(), request));
    }
}
