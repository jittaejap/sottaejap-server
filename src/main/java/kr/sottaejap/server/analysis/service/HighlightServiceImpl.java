package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * '나만의 특징' 한 문장을 AI에게 받는다 (⑨ · FR-11-03 · E-75). {@code ClusterNamingServiceImpl}과 같은 모양이다 —
 * 트랜잭션 밖에서 부르고, 실패하면 템플릿으로 갈음한다.
 *
 * <p>AI에 보내는 것은 집계뿐이고 {@code pending}은 빼고 보낸다 (E-75). 판정이 없는 금액은 문장에 쓸 근거가
 * 아니어서, 애초에 건네지 않는 편이 프롬프트로 막는 것보다 확실하다 (NFR-02).
 *
 * <p>{@code fallback: true}는 AI가 LLM 없이 정적 문장을 돌려줬다는 뜻이다 (E-38). 그 문장은 집계를 보지 않으므로
 * 우리 템플릿을 쓴다 — 최소한 이 사용자의 카테고리와 금액은 맞다.
 */
@Service
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    /** AI는 task_context.state로 판단한다 — message는 작업을 알리는 한 문장이면 된다 (05 §3). */
    private static final String NARRATE_MESSAGE = "이번 달 소비의 특징을 한 문장으로 정리해 주세요";

    private final AiClient aiClient;

    @Override
    public String highlight(long userId, YearMonth analysisYearMonth, AnalysisSummary summary) {
        try {
            ChatResponse response = aiClient.chat(new ChatRequest(
                    NARRATE_MESSAGE,
                    String.valueOf(userId),
                    new TaskContext(TaskType.ANALYSIS_NARRATE, RetrospectStatus.ACTIVE,
                            state(analysisYearMonth, summary)),
                    List.of()));
            String reply = response.reply() == null ? "" : response.reply().strip();
            if (reply.isBlank() || response.isFallback()) {
                return HighlightTemplate.highlightFor(summary);
            }
            return reply;
        } catch (BusinessException llmUnavailable) {
            return HighlightTemplate.highlightFor(summary);
        }
    }

    /**
     * 05 §3 ANALYSIS_NARRATE state. 키는 <b>중첩 객체까지</b> snake_case다 — {@code RetrospectChatSupport.buildState} ·
     * {@code ClusterNamingServiceImpl.state}와 같은 모양이고, AGENTS.md의 "경계는 snake_case"가 여기에도 걸린다.
     *
     * <p>집계 record를 그대로 싣지 않고 손으로 옮긴다. 같은 record가 외부 {@code GET /analysis} 응답에도 실려
     * camelCase로 나가야 하므로, record에 {@code @JsonProperty}를 달아 해결할 수 없다.
     */
    private Map<String, Object> state(YearMonth analysisYearMonth, AnalysisSummary summary) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("analysis_year_month", analysisYearMonth == null ? null : analysisYearMonth.toString());
        state.put("by_verdict", summary.byVerdict().stream().map(HighlightServiceImpl::verdictState).toList());
        state.put("by_category", summary.byCategory().stream().map(HighlightServiceImpl::categoryState).toList());
        return state;
    }

    /** 값이 null일 수 있으므로 Map.of가 아니라 LinkedHashMap을 쓴다 — share는 예산이 없으면 null이다. */
    private static Map<String, Object> verdictState(VerdictSummary row) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("verdict", row.verdict().name());
        state.put("cluster_count", row.clusterCount());
        state.put("monthly_total_amount", row.monthlyTotalAmount());
        state.put("share", row.share());
        return state;
    }

    private static Map<String, Object> categoryState(CategorySummary row) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("category", row.category());
        state.put("dominant_time_slot", row.dominantTimeSlot() == null ? null : row.dominantTimeSlot().name());
        state.put("avg_amount", row.avgAmount());
        state.put("monthly_total_amount", row.monthlyTotalAmount());
        state.put("verdict", row.verdict() == null ? null : row.verdict().name());
        return state;
    }
}
