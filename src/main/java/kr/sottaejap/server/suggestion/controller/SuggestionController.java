package kr.sottaejap.server.suggestion.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionListResponse;
import kr.sottaejap.server.suggestion.dto.SuggestionView;
import kr.sottaejap.server.suggestion.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 05 §2 #15 · #16 — 행동 조정안 (FR-08).
 *
 * <p>제안을 <b>만드는</b> 경로가 없다. 제안은 회고 저장 때 도는 재계산이 만드는 파생 행이다 (E-81).
 */
@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    /** {@code status}를 생략하면 PROPOSED · ADOPTED만. 잘못된 값은 전역 핸들러가 400으로 바꾼다. */
    @GetMapping
    public ApiResponse<SuggestionListResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(required = false) SuggestionStatus status) {
        return ApiResponse.success(suggestionService.list(user.userId(), status));
    }

    @PostMapping("/{suggestionId}/adopt")
    public ApiResponse<SuggestionView> adopt(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable long suggestionId,
                                             @Valid @RequestBody SuggestionAdoptRequest request) {
        return ApiResponse.success(suggestionService.adopt(user.userId(), suggestionId, request));
    }

    @PostMapping("/{suggestionId}/reject")
    public ApiResponse<SuggestionView> reject(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable long suggestionId) {
        return ApiResponse.success(suggestionService.reject(user.userId(), suggestionId));
    }
}
