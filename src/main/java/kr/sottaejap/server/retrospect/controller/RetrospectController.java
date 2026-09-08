package kr.sottaejap.server.retrospect.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.service.RetrospectService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 05 §2 회고 3종 (API 8 · 10 · 11). skip(9)은 두지 않는다 (E-65). */
@RestController
@RequestMapping("/retrospects")
@RequiredArgsConstructor
public class RetrospectController {

    private final RetrospectService retrospectService;

    /** 후보 + 선정 근거. limit 기본 1(알림 경로), from·to는 채팅 3일 창·날짜 지정 (E-48 · E-62). */
    @GetMapping("/candidates")
    public ApiResponse<CandidateListResponse> candidates(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(retrospectService.candidates(user.userId(), limit, from, to));
    }

    /** 회고 저장 → 규칙 엔진 재계산 → 리프 묶음 응답 (FR-04-14). */
    @PostMapping
    public ApiResponse<RetrospectSaveResponse> save(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody RetrospectSaveRequest request) {
        return ApiResponse.success(retrospectService.save(user.userId(), request));
    }

    /** 대화형 회고 턴 — 상태 없는 AI 프록시 (E-63). */
    @PostMapping("/chat")
    public ApiResponse<RetrospectChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody RetrospectChatRequest request) {
        return ApiResponse.success(retrospectService.chat(user.userId(), request));
    }
}
