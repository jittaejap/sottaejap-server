package kr.sottaejap.server.internalai;

import jakarta.validation.Valid;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.internalai.dto.InternalReflectionRequest;
import kr.sottaejap.server.retrospect.dto.MemoryResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.service.RetrospectService;
import kr.sottaejap.server.suggestion.dto.SuggestionListResponse;
import kr.sottaejap.server.suggestion.service.SuggestionService;
import kr.sottaejap.server.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI `SpringClient` 6개 메서드와 1:1인 내부 조회·저장 API (05 §3). 응답은 camelCase 봉투다.
 *
 * <p>v2.6: 여섯 경로가 모두 실구현이다 — 빈 목록 스텁이 남아 있지 않다 (E-73 · E-81).
 * 응답 `data`는 반드시 object여야 AI `SpringClient`가 봉투를 벗긴다.
 */
@RestController
@RequestMapping("/internal/ai/users/{userId}")
@RequiredArgsConstructor
public class InternalAiController {

    private final TransactionService transactionService;
    private final RetrospectService retrospectService;
    private final AnalysisService analysisService;
    private final SuggestionService suggestionService;

    /** SpringClient.get_transactions — 업로드된 거래를 그대로 돌려준다. 계산·판정은 없다. */
    @GetMapping("/transactions")
    public ApiResponse<Map<String, Object>> getTransactions(
            @PathVariable long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(Map.of(
                "transactions", transactionService.findForAi(userId, from, to, category, size)));
    }

    /** SpringClient.get_reflections */
    @GetMapping("/reflections")
    public ApiResponse<Map<String, Object>> getReflections(@PathVariable long userId) {
        return ApiResponse.success(Map.of("reflections", retrospectService.findReflections(userId)));
    }

    /** SpringClient.save_reflection — 외부 POST /retrospects와 같은 검증·응답. source는 CANDIDATE (E-66). */
    @PostMapping("/reflections")
    public ApiResponse<RetrospectSaveResponse> saveReflection(@PathVariable long userId,
                                                              @Valid @RequestBody InternalReflectionRequest request) {
        return ApiResponse.success(retrospectService.save(userId, request.toSaveRequest()));
    }

    /**
     * SpringClient.get_behavior_analysis — 외부 GET /analysis + GET /satisfaction-map의 points.
     * highlight는 싣지 않는다 (E-75) — 그 문장을 만드는 게 AI의 일이다.
     */
    @GetMapping("/analysis")
    public ApiResponse<InternalAnalysisResponse> getBehaviorAnalysis(@PathVariable long userId) {
        return ApiResponse.success(analysisService.internalAnalysis(userId));
    }

    /**
     * SpringClient.get_action_plan — 외부 GET /suggestions의 기본 목록(PROPOSED · ADOPTED)과 같다.
     * AI는 `suggestions[].id`를 `state.suggestion_ids`와 대조해 고른다 (E-81).
     */
    @GetMapping("/suggestions")
    public ApiResponse<SuggestionListResponse> getActionPlan(@PathVariable long userId) {
        return ApiResponse.success(suggestionService.internalList(userId));
    }

    /** SpringClient.get_memory — 개인 소비 메모리 요약 */
    @GetMapping("/memory")
    public ApiResponse<MemoryResponse> getMemory(@PathVariable long userId) {
        return ApiResponse.success(retrospectService.memory(userId));
    }
}
