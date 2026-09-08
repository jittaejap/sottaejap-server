package kr.sottaejap.server.analysis.controller;

import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 05 §2 API 22 — 소비 분석 (FR-11).
 *
 * <p>'나만의 특징' 한 문장 때문에 AI 왕복이 이 GET 안에 들어 있다 (E-75). AI가 없어도 200이다 —
 * 폴백 문장이 있고, 집계는 애초에 AI와 무관하다.
 */
@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/analysis")
    public ApiResponse<AnalysisResponse> analysis(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(analysisService.analysis(user.userId()));
    }
}
