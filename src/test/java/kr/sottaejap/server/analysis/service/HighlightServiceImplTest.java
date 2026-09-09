package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** '나만의 특징' 한 문장 (⑨ · E-75) — AI가 없거나 못 미더우면 템플릿으로 갈음한다. */
@ExtendWith(MockitoExtension.class)
class HighlightServiceImplTest {

    private static final long USER_ID = 7L;
    private static final YearMonth ANALYSIS_MONTH = YearMonth.of(2026, 8);

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private HighlightServiceImpl service;

    @Test
    void AI가_준_문장을_그대로_쓴다() {
        when(aiClient.chat(any())).thenReturn(reply("배달이 예산의 10%였어요."));

        assertEquals("배달이 예산의 10%였어요.", service.highlight(USER_ID, ANALYSIS_MONTH, summary()));
    }

    @Test
    void state에는_집계만_싣고_pending은_보내지_않는다() {
        when(aiClient.chat(any())).thenReturn(reply("한 문장"));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        Map<String, Object> state = capturedState();

        assertEquals(TaskType.ANALYSIS_NARRATE, capturedTaskContext().task());
        assertEquals("2026-08", state.get("analysis_year_month"));
        assertFalse(state.containsKey("pending"), "보류 금액은 문장에 쓸 근거가 아니다 (E-75)");
    }

    /** 중첩 객체 키까지 snake_case다 — AI가 `monthly_total_amount`로 읽는다 (AGENTS.md · 05 §3). */
    @Test
    void state의_중첩_객체도_snake_case로_보낸다() {
        when(aiClient.chat(any())).thenReturn(reply("한 문장"));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        Map<String, Object> state = capturedState();

        assertEquals(List.of(
                        orderedMap("verdict", "SUSTAIN", "cluster_count", 1,
                                "monthly_total_amount", 168_000, "share", 0.168),
                        orderedMap("verdict", "ADJUST", "cluster_count", 1,
                                "monthly_total_amount", 96_000, "share", 0.096)),
                state.get("by_verdict"));

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("category", "배달");
        category.put("dominant_time_slot", "NIGHT");
        category.put("avg_amount", 12_000);
        category.put("monthly_total_amount", 96_000);
        category.put("verdict", "ADJUST");
        assertEquals(List.of(category), state.get("by_category"));
    }

    /** 시간대를 키에 넣지 않는 카테고리와 전부 보류인 카테고리는 null이다 (E-58 · E-73). */
    @Test
    void 집계가_null인_자리는_null로_보낸다() {
        when(aiClient.chat(any())).thenReturn(reply("한 문장"));
        AnalysisSummary summary = new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 0, 0, null),
                        new VerdictSummary(Verdict.ADJUST, 0, 0, null)),
                new PendingSummary(1, 24_000, null),
                List.of(new CategorySummary("교통", null, null, 24_000, null)));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary);

        Map<String, Object> state = capturedState();

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("category", "교통");
        category.put("dominant_time_slot", null);
        category.put("avg_amount", null);
        category.put("monthly_total_amount", 24_000);
        category.put("verdict", null);
        assertEquals(List.of(category), state.get("by_category"));
    }

    @Test
    void AI가_빈_문장을_주면_템플릿을_쓴다() {
        when(aiClient.chat(any())).thenReturn(reply("   "));

        assertEquals(HighlightTemplate.highlightFor(summary()),
                service.highlight(USER_ID, ANALYSIS_MONTH, summary()));
    }

    @Test
    void 폴백_응답은_집계를_보지_않으므로_템플릿을_쓴다() {
        when(aiClient.chat(any())).thenReturn(new ChatResponse("정적 문장", List.of(), null, true));

        assertEquals(HighlightTemplate.highlightFor(summary()),
                service.highlight(USER_ID, ANALYSIS_MONTH, summary()));
    }

    @Test
    void AI가_503이면_템플릿을_쓴다() {
        when(aiClient.chat(any())).thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        assertEquals(HighlightTemplate.highlightFor(summary()),
                service.highlight(USER_ID, ANALYSIS_MONTH, summary()));
    }

    @Test
    void 기준월이_없어도_state를_만들_수_있다() {
        when(aiClient.chat(any())).thenReturn(reply("한 문장"));

        assertEquals("한 문장", service.highlight(USER_ID, null, summary()));
    }

    @Test
    void 집계가_같으면_AI를_다시_부르지_않는다() {
        when(aiClient.chat(any())).thenReturn(reply("배달이 예산의 10%였어요."));

        String first = service.highlight(USER_ID, ANALYSIS_MONTH, summary());
        String second = service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        assertEquals(first, second);
        verify(aiClient, times(1)).chat(any());
    }

    @Test
    void 집계가_바뀌면_AI를_다시_부른다() {
        // 회고를 저장하면 재계산이 집계를 바꾼다 — 키가 집계라 무효화 훅 없이 miss가 난다.
        when(aiClient.chat(any())).thenReturn(reply("첫 문장"), reply("새 문장"));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        assertEquals("새 문장", service.highlight(USER_ID, ANALYSIS_MONTH, otherSummary()));
        verify(aiClient, times(2)).chat(any());
    }

    @Test
    void 기준월이_바뀌면_AI를_다시_부른다() {
        when(aiClient.chat(any())).thenReturn(reply("8월 문장"), reply("9월 문장"));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        assertEquals("9월 문장", service.highlight(USER_ID, YearMonth.of(2026, 9), summary()));
        verify(aiClient, times(2)).chat(any());
    }

    @Test
    void 사용자가_다르면_남의_문장을_주지_않는다() {
        when(aiClient.chat(any())).thenReturn(reply("7번 문장"), reply("8번 문장"));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        assertEquals("8번 문장", service.highlight(USER_ID + 1, ANALYSIS_MONTH, summary()));
        verify(aiClient, times(2)).chat(any());
    }

    @Test
    void 폴백_응답은_캐시하지_않는다() {
        // 집계를 보지 않은 문장이라, AI가 살아나면 다시 물어야 한다 (E-38).
        when(aiClient.chat(any())).thenReturn(new ChatResponse("정적 문장", List.of(), null, true));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());
        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        verify(aiClient, times(2)).chat(any());
    }

    @Test
    void AI가_503이어도_캐시하지_않는다() {
        when(aiClient.chat(any())).thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        service.highlight(USER_ID, ANALYSIS_MONTH, summary());
        service.highlight(USER_ID, ANALYSIS_MONTH, summary());

        verify(aiClient, times(2)).chat(any());
    }

    private Map<String, Object> capturedState() {
        return capturedTaskContext().state();
    }

    private TaskContext capturedTaskContext() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        return captor.getValue().taskContext();
    }

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static ChatResponse reply(String reply) {
        return new ChatResponse(reply, List.of(), null, false);
    }

    /** 회고를 한 건 더 저장해 배달 합계와 판정이 바뀐 모습. */
    private static AnalysisSummary otherSummary() {
        return new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 1, 168_000, 0.168),
                        new VerdictSummary(Verdict.ADJUST, 1, 120_000, 0.120)),
                new PendingSummary(1, 24_000, 0.024),
                List.of(new CategorySummary("배달", TimeSlot.NIGHT, 15_000, 120_000, Verdict.ADJUST)));
    }

    private static AnalysisSummary summary() {
        return new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 1, 168_000, 0.168),
                        new VerdictSummary(Verdict.ADJUST, 1, 96_000, 0.096)),
                new PendingSummary(1, 24_000, 0.024),
                List.of(new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 96_000, Verdict.ADJUST)));
    }
}
