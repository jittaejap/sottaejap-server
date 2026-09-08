package kr.sottaejap.server.analysis.controller;

import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 05 §2 API 14 — 만족도 지도 (FR-07). */
@RestController
@RequiredArgsConstructor
public class SatisfactionMapController {

    private final AnalysisService analysisService;

    @GetMapping("/satisfaction-map")
    public ApiResponse<SatisfactionMapResponse> satisfactionMap(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(analysisService.satisfactionMap(user.userId()));
    }
}
