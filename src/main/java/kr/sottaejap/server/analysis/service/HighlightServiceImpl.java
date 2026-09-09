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
import java.util.concurrent.ConcurrentHashMap;

/**
 * '나만의 특징' 한 문장을 AI에게 받는다 (⑨ · FR-11-03 · E-75). {@code ClusterNamingServiceImpl}과 같은 모양이다 —
 * 트랜잭션 밖에서 부르고, 실패하면 템플릿으로 갈음한다.
 *
 * <p>AI에 보내는 것은 집계뿐이고 {@code pending}은 빼고 보낸다 (E-75). 판정이 없는 금액은 문장에 쓸 근거가
 * 아니어서, 애초에 건네지 않는 편이 프롬프트로 막는 것보다 확실하다 (NFR-02).
 *
 * <p>{@code fallback: true}는 AI가 LLM 없이 정적 문장을 돌려줬다는 뜻이다 (E-38). 그 문장은 집계를 보지 않으므로
 * 우리 템플릿을 쓴다 — 최소한 이 사용자의 카테고리와 금액은 맞다.
 *
 * <p><b>집계가 같으면 AI를 다시 부르지 않는다.</b> 집계는 결정론이고(E-18) 이 문장은 집계만 보고 만들므로
 * (E-75 · NFR-02), 같은 집계에서 다른 문장이 나올 이유가 없다. 캐시가 없으면 S15는 새로고침 · 탭 전환마다
 * {@code AI_TIMEOUT_MS}(15초)를 기다린다 (PR #16 리뷰 #6).
 */
@Service
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    /** AI는 task_context.state로 판단한다 — message는 작업을 알리는 한 문장이면 된다 (05 §3). */
    private static final String NARRATE_MESSAGE = "이번 달 소비의 특징을 한 문장으로 정리해 주세요";

    private final AiClient aiClient;

    /**
     * 사용자별로 <b>한 칸</b>이다. 집계가 바뀌면 그 칸을 통째로 갈아 끼우므로 무효화 훅이 필요 없다 —
     * {@code ClusterRecomputeService.recomputeAll}을 건드리지 않는다. 옛 집계를 키로 쌓아 두면 회고를 저장할
     * 때마다 항목이 늘지만, 한 칸이면 항목 수가 사용자 수를 넘지 않는다.
     *
     * <p>ponytail: 인메모리이고 인스턴스가 하나라는 전제다 (07 §1 · {@code NotificationServiceImpl}과 같은 전제).
     * 인스턴스가 늘면 같은 문장을 인스턴스 수만큼 만들고, 사용자가 수만 명이 되면 상한이 필요하다. 그때
     * DB 컬럼이나 LRU로 옮긴다. 배포하면 비는 것은 문제가 아니라 이점이다 — 프롬프트를 고치면 옛 문장이 남지 않는다.
     */
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    /**
     * 캐시 키는 집계 그 자체다. {@link AnalysisSummary} 이하가 전부 record라 {@code equals}가 값 비교다.
     *
     * <p><b>AI가 {@code task_context.state}만 보고 문장을 쓴다는 전제에 기댄다</b> (05 §3 · E-102, 01 v2.26 정정).
     * {@code ANALYSIS_NARRATE}에는 전용 핸들러가 있지만(ai PR #43 {@code analysis_narrate.py}) 도구를 부르지 않고
     * state 한 번으로 생성하며, {@code recent_messages}도 비워 보내므로 AI 입력은 {@link #state}가 싣는 것이
     * 전부다 — 이 키가 그것을 빠짐없이 덮는다. <b>ai가 그 핸들러에 도구(`/internal/ai/users/{id}/analysis`)를
     * 붙이면 그 응답에는 묶음 단위 {@code points}가 있어 집계가 같아도 문장 재료가 달라진다. 그때 이 키를 다시
     * 봐야 한다.</b>
     *
     * <p>기준월이 {@code null}이어도 record라 그냥 같다고 나오지만, 실제로 그 값이 여기까지 오지는 않는다 —
     * 기준월이 없으면 거래가 0건이고, 그러면 묶음도 없어 {@code AnalysisServiceImpl}의 가드(E-75)가 먼저
     * 템플릿을 돌려준다. 테스트에서만 밟는 경로다.
     */
    private record Key(YearMonth analysisYearMonth, AnalysisSummary summary) {
    }

    private record Cached(Key key, String highlight) {
    }

    @Override
    public String highlight(long userId, YearMonth analysisYearMonth, AnalysisSummary summary) {
        Key key = new Key(analysisYearMonth, summary);
        Cached cached = cache.get(userId);
        if (cached != null && cached.key().equals(key)) {
            return cached.highlight();
        }
        try {
            ChatResponse response = aiClient.chat(new ChatRequest(
                    NARRATE_MESSAGE,
                    String.valueOf(userId),
                    new TaskContext(TaskType.ANALYSIS_NARRATE, RetrospectStatus.ACTIVE,
                            state(analysisYearMonth, summary)),
                    List.of()));
            String reply = response.reply() == null ? "" : response.reply().strip();
            // 폴백 · 빈 문장은 집계를 보지 않은 문장이라 캐시하지 않는다 — AI가 돌아오면 다시 부른다 (E-38).
            if (reply.isBlank() || response.isFallback()) {
                return HighlightTemplate.highlightFor(summary);
            }
            cache.put(userId, new Cached(key, reply));
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
