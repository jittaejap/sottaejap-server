package kr.sottaejap.server.analysis.controller;

import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.BehaviorListResponse;
import kr.sottaejap.server.analysis.service.BehaviorService;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 05 §2 — 묶음 목록 · 상세 (FR-07-05). 지도와 같은 데이터를 목록으로 보는 경로다. */
@RestController
@RequestMapping("/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    @GetMapping
    public ApiResponse<BehaviorListResponse> behaviors(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(behaviorService.behaviors(user.userId()));
    }

    @GetMapping("/{behaviorId}")
    public ApiResponse<BehaviorDetailResponse> behavior(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable long behaviorId) {
        return ApiResponse.success(behaviorService.behavior(user.userId(), behaviorId));
    }
}
