package kr.sottaejap.server.onboarding.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.onboarding.service.OnboardingService;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 05 §2 #18 · #21 — 온보딩 (FR-09-01,02). */
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /** 표본 회고 시작. 응답은 `GET /retrospects/candidates`와 같은 모양이다 (E-92). */
    @PostMapping("/start")
    public ApiResponse<CandidateListResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody OnboardingStartRequest request) {
        return ApiResponse.success(onboardingService.start(user.userId(), request));
    }

    /** 온보딩 완료. 본문이 없다 — 클라이언트는 빈 POST로 부른다. */
    @PostMapping("/complete")
    public ApiResponse<OnboardingCompleteResponse> complete(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(onboardingService.complete(user.userId()));
    }
}
