package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.chat.dto.AnalysisChatRequest;
import kr.sottaejap.server.chat.dto.AnalysisChatResponse;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소비 분석 자유 질문은 판단을 AI에 맡긴다 (E-104). 확인할 것은 무엇을 실어 보내는지 — task · state 키 · 최근 대화 —
 * 와 무엇을 그대로 돌려주는지다.
 */
class AnalysisChatServiceImplTest {

    private static final long USER_ID = 1L;

    private AiClient aiClient;
    private TransactionService transactionService;
    private AnalysisChatServiceImpl service;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        transactionService = mock(TransactionService.class);
        service = new AnalysisChatServiceImpl(aiClient, transactionService);

        when(transactionService.analysisYearMonth(USER_ID)).thenReturn(YearMonth.of(2026, 8));
        when(aiClient.chat(any())).thenReturn(new ChatResponse("배달 묶음은 …", List.of(), false, false));
    }

    @Test
    void sendsAnalysisTaskWithYearMonthOnlyState() {
        service.ask(USER_ID, new AnalysisChatRequest("배달은 왜 조정 대상이에요?", null));

        ChatRequest sent = sentRequest();
        assertThat(sent.taskContext().task()).isEqualTo(TaskType.ANALYSIS);
        assertThat(sent.taskContext().status()).isEqualTo(RetrospectStatus.ACTIVE);
        // 05 §3 ANALYSIS state — 키는 analysis_year_month 하나뿐이고 집계는 AI가 내부 API로 읽는다.
        assertThat(sent.taskContext().state()).containsExactly(
                java.util.Map.entry(AnalysisChatServiceImpl.ANALYSIS_YEAR_MONTH_KEY, "2026-08"));
        assertThat(sent.message()).isEqualTo("배달은 왜 조정 대상이에요?");
        assertThat(sent.userId()).isEqualTo("1");
    }

    @Test
    void sendsNullYearMonthWhenUserHasNoTransactions() {
        when(transactionService.analysisYearMonth(USER_ID)).thenReturn(null);

        service.ask(USER_ID, new AnalysisChatRequest("이번 달은 어땠어요?", null));

        // 키는 있고 값만 null이다 — 문서가 "키는 필수"라고 하므로 키를 빼지 않는다 (05 §3).
        assertThat(sentRequest().taskContext().state())
                .containsKey(AnalysisChatServiceImpl.ANALYSIS_YEAR_MONTH_KEY)
                .containsValue(null);
    }

    @Test
    void forwardsOnlyTheLastSixRecentMessagesInOrder() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            history.add(new ChatMessage(i % 2 == 1 ? "user" : "assistant", "m" + i));
        }

        service.ask(USER_ID, new AnalysisChatRequest("그럼 배달은요?", history));

        assertThat(sentRequest().recentMessages())
                .extracting(ChatMessage::content)
                .containsExactly("m3", "m4", "m5", "m6", "m7", "m8");
    }

    @Test
    void passesFallbackThrough() {
        when(aiClient.chat(any())).thenReturn(new ChatResponse("지금은 AI 설명을 준비할 수 없어요.", List.of(), false, true));

        AnalysisChatResponse response = service.ask(USER_ID, new AnalysisChatRequest("배달은요?", null));

        assertThat(response.fallback()).isTrue();
        assertThat(response.reply()).isEqualTo("지금은 AI 설명을 준비할 수 없어요.");
    }

    private ChatRequest sentRequest() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        return captor.getValue();
    }
}
