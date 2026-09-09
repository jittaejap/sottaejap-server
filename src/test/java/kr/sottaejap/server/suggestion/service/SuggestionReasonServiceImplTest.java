package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 제안 이유 문장 (05 §3 ACTION_PLAN · E-64). AI 왕복은 트랜잭션 밖이고, 설명이 아닌 응답은 저장하지 않는다 —
 * 그때는 화면이 템플릿 문구를 쓴다 (E-38).
 */
@ExtendWith(MockitoExtension.class)
class SuggestionReasonServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long BEHAVIOR_ID = 12L;
    private static final long SUGGESTION_ID = 42L;
    private static final String EXPLANATION = "심야 배달을 두 번만 줄여도 24,000원이 남아요.";

    @Mock
    private SuggestionRepository suggestionRepository;
    @Mock
    private AiClient aiClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SuggestionReasonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SuggestionReasonServiceImpl(suggestionRepository, aiClient, transactionManager);
    }

    @Test
    void 열린_제안이_없으면_AI를_부르지_않는다() {
        when(suggestionRepository.findProposedByUserIdAndBehaviorId(USER_ID, BEHAVIOR_ID))
                .thenReturn(Optional.empty());

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        verifyNoInteractions(aiClient);
    }

    @Test
    void 이미_이유가_있으면_다시_짓지_않는다() {
        Suggestion explained = suggestion();
        explained.explain("이미 있는 문장");
        given(explained);

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        verifyNoInteractions(aiClient);
        assertEquals("이미 있는 문장", explained.getReason());
    }

    @Test
    void AI가_준_문장을_그_제안에_채운다() {
        Suggestion suggestion = suggestion();
        given(suggestion);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply(EXPLANATION));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        assertEquals(EXPLANATION, suggestion.getReason());
    }

    @Test
    void state에는_그_제안_id_하나만_싣는다() {
        given(suggestion());
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply(EXPLANATION));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        ChatRequest sent = captor.getValue();
        assertEquals(TaskType.ACTION_PLAN, sent.taskContext().task());
        assertEquals(String.valueOf(USER_ID), sent.userId());
        assertEquals(Map.of("suggestion_ids", List.of(SUGGESTION_ID)), sent.taskContext().state());
    }

    @Test
    void AI가_503이면_채우지_않는다() {
        Suggestion suggestion = suggestion();
        given(suggestion);
        when(aiClient.chat(any(ChatRequest.class)))
                .thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        assertNull(suggestion.getReason());
    }

    @Test
    void 폴백_응답은_채우지_않는다() {
        // ACTION_PLAN 폴백은 "지금은 설명드릴 수 없어요"라는 안내다. 저장하면 이유 자리에 사과문이 남는다.
        Suggestion suggestion = suggestion();
        given(suggestion);
        when(aiClient.chat(any(ChatRequest.class)))
                .thenReturn(new ChatResponse("지금은 제안 이유를 설명드릴 수 없어요.", List.of(), null, true));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        assertNull(suggestion.getReason());
    }

    @Test
    void AI가_제안을_읽지_못했으면_채우지_않는다() {
        // AI → Spring 내부 조회 실패는 tool_results[].success = false로 온다 (05 §3 타임아웃 표).
        Suggestion suggestion = suggestion();
        given(suggestion);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                "해당하는 제안을 찾을 수 없어요.",
                List.of(Map.of("tool_name", "action_plan", "success", false)),
                null, false));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        assertNull(suggestion.getReason());
    }

    @Test
    void 빈_문장은_채우지_않는다() {
        Suggestion suggestion = suggestion();
        given(suggestion);
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(reply("   "));

        service.explainProposed(USER_ID, BEHAVIOR_ID);

        assertNull(suggestion.getReason());
    }

    private void given(Suggestion suggestion) {
        when(suggestionRepository.findProposedByUserIdAndBehaviorId(USER_ID, BEHAVIOR_ID))
                .thenReturn(Optional.of(suggestion));
    }

    private static Suggestion suggestion() {
        Suggestion suggestion = Suggestion.propose(BEHAVIOR_ID, 8, 96_000);
        ReflectionTestUtils.setField(suggestion, "id", SUGGESTION_ID);
        return suggestion;
    }

    private static ChatResponse reply(String reply) {
        return new ChatResponse(reply, List.of(Map.of("tool_name", "action_plan", "success", true)), null, false);
    }
}
