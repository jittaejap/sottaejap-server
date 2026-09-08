package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        Map<String, Object> state = captor.getValue().taskContext().state();

        assertEquals(TaskType.ANALYSIS_NARRATE, captor.getValue().taskContext().task());
        assertEquals("2026-08", state.get("analysis_year_month"));
        assertEquals(summary().byVerdict(), state.get("by_verdict"));
        assertEquals(summary().byCategory(), state.get("by_category"));
        assertFalse(state.containsKey("pending"), "보류 금액은 문장에 쓸 근거가 아니다 (E-75)");
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

    private static ChatResponse reply(String reply) {
        return new ChatResponse(reply, List.of(), null, false);
    }

    private static AnalysisSummary summary() {
        return new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 1, 168_000, 0.168),
                        new VerdictSummary(Verdict.ADJUST, 1, 96_000, 0.096)),
                new PendingSummary(1, 24_000, 0.024),
                List.of(new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 96_000, Verdict.ADJUST)));
    }
}
