package kr.sottaejap.server.goal.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.goal.dto.GoalListResponse;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 05 §2 #4 · #5 · #5a — 목표 (FR-01-02,05 · FR-08-04). */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ApiResponse<GoalListResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(goalService.list(user.userId()));
    }

    @PostMapping
    public ApiResponse<GoalView> create(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody GoalRequest request) {
        return ApiResponse.success(goalService.create(user.userId(), request));
    }

    @PutMapping("/{goalId}")
    public ApiResponse<GoalView> update(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable long goalId,
                                        @Valid @RequestBody GoalRequest request) {
        return ApiResponse.success(goalService.update(user.userId(), goalId, request));
    }

    /** soft delete라 돌려줄 것이 없다. 봉투는 `{ "success": true }`다. */
    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable long goalId) {
        goalService.delete(user.userId(), goalId);
        return ApiResponse.success();
    }
}
