package kr.sottaejap.server.internalai;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI `SpringClient` 6개 메서드와 1:1인 내부 조회·저장 API (05 §3). 응답은 camelCase 봉투다.
 *
 * <p>스캐폴딩 단계: 조회 5종은 빈 목록, 저장 1종은 501 NOT_IMPLEMENTED. 도메인이 붙는 순서대로 채운다.
 */
@RestController
@RequestMapping("/internal/ai/users/{userId}")
public class InternalAiController {

    /** SpringClient.get_transactions */
    @GetMapping("/transactions")
    public ApiResponse<Map<String, Object>> getTransactions(@PathVariable long userId,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(Map.of("transactions", List.of()));
    }

    /** SpringClient.get_reflections */
    @GetMapping("/reflections")
    public ApiResponse<Map<String, Object>> getReflections(@PathVariable long userId) {
        return ApiResponse.success(Map.of("reflections", List.of()));
    }

    /** SpringClient.save_reflection — 외부 POST /retrospects와 같은 검증·응답 */
    @PostMapping("/reflections")
    public ApiResponse<Void> saveReflection(@PathVariable long userId, @RequestBody InternalReflectionRequest request) {
        throw new BusinessException(CommonErrorCode.NOT_IMPLEMENTED);
    }

    /** SpringClient.get_behavior_analysis — 외부 GET /analysis + GET /satisfaction-map의 points */
    @GetMapping("/analysis")
    public ApiResponse<Map<String, Object>> getBehaviorAnalysis(@PathVariable long userId) {
        return ApiResponse.success(Map.of("byVerdict", List.of(), "byCategory", List.of(), "points", List.of()));
    }

    /** SpringClient.get_action_plan — 외부 GET /suggestions와 동일 */
    @GetMapping("/suggestions")
    public ApiResponse<Map<String, Object>> getActionPlan(@PathVariable long userId) {
        return ApiResponse.success(Map.of("suggestions", List.of()));
    }

    /** SpringClient.get_memory */
    @GetMapping("/memory")
    public ApiResponse<Map<String, Object>> getMemory(@PathVariable long userId) {
        return ApiResponse.success(Map.of("clusters", List.of(), "recentReflections", List.of()));
    }
}
