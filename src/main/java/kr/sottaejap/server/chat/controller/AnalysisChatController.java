package kr.sottaejap.server.chat.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.chat.dto.AnalysisChatRequest;
import kr.sottaejap.server.chat.dto.AnalysisChatResponse;
import kr.sottaejap.server.chat.service.AnalysisChatService;
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
public class AnalysisChatController {

    private final AnalysisChatService analysisChatService;

    /** 소비 분석 자유 질문 (05 #28 · E-104). 집계는 AI가 내부 API로 읽고 답한다 — Spring은 판단하지 않는다. */
    @PostMapping("/analysis")
    public ApiResponse<AnalysisChatResponse> analysis(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @RequestBody @Valid AnalysisChatRequest request) {
        return ApiResponse.success(analysisChatService.ask(user.userId(), request));
    }
}
