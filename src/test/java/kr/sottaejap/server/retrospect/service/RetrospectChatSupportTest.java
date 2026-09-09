package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.ReflectionStep;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.dto.ReflectionDraft;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST /retrospects/chat 프록시 (E-63). state 계약 · 응답 정화(E-20) · 다음 단계 계산 · 오류 분기를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectChatSupportTest {

    private static final long USER_ID = 1L;
    private static final long TRANSACTION_ID = 1043L;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RetrospectRepository retrospectRepository;
    @Mock
    private CandidateService candidateService;
    @Mock
    private AiClient aiClient;

    @InjectMocks
    private RetrospectChatSupport support;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = Transaction.of(USER_ID, OffsetDateTime.parse("2026-08-22T14:10:00Z"),
                "○○배달", 12000, "배달", null, "h");
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void state는_05_3_REFLECTION_구조와_같은_키로_AI에_간다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.TIMESLOT_OUTLIER);
        givenAiReply(new ChatResponse("만족하셨나요?", null, null, false));

        support.chat(USER_ID, new RetrospectChatRequest(TRANSACTION_ID, "그냥 배고파서요",
                ReflectionStep.SATISFACTION, new ReflectionDraft(Satisfaction.UNKNOWN, null, null, null), null));

        ChatRequest sent = captureChatRequest();
        assertEquals("1", sent.userId());
        assertEquals(TaskType.REFLECTION, sent.taskContext().task());
        assertEquals(RetrospectStatus.ACTIVE, sent.taskContext().status());

        Map<String, Object> state = sent.taskContext().state();
        assertEquals(List.of("transaction", "reason_code", "reflection", "step"), List.copyOf(state.keySet()));
        assertEquals("TIMESLOT_OUTLIER", state.get("reason_code"));
        assertEquals("SATISFACTION", state.get("step"));

        Map<String, Object> transactionState = (Map<String, Object>) state.get("transaction");
        assertEquals(List.of("id", "occurred_at", "merchant", "amount", "category", "time_slot"),
                List.copyOf(transactionState.keySet()));
        assertEquals(TRANSACTION_ID, transactionState.get("id"));
        // KST로 옮긴 ISO 오프셋 문자열. 초가 0이면 OffsetDateTime.toString이 초를 생략한다.
        assertEquals("2026-08-22T23:10+09:00", transactionState.get("occurred_at"));
        assertEquals("○○배달", transactionState.get("merchant"));
        assertEquals(12000, transactionState.get("amount"));
        assertEquals("배달", transactionState.get("category"));
        assertEquals("NIGHT", transactionState.get("time_slot"));

        Map<String, Object> reflectionState = (Map<String, Object>) state.get("reflection");
        assertEquals(List.of("satisfaction", "purpose", "companion", "repeat_intention"),
                List.copyOf(reflectionState.keySet()));
        assertEquals("UNKNOWN", reflectionState.get("satisfaction"));
        assertNull(reflectionState.get("purpose"));
        assertNull(reflectionState.get("companion"));
        assertNull(reflectionState.get("repeat_intention"));
    }

    @Test
    void INTRO에서_message가_없으면_고정_문구로_AI에_보낸다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("지난 금요일 밤 배달이에요.", null, null, false));

        support.chat(USER_ID, new RetrospectChatRequest(TRANSACTION_ID, null, null, null, null));

        String message = captureChatRequest().message();
        assertFalse(message == null || message.isBlank());
        assertEquals(RetrospectChatSupport.INTRO_MESSAGE, message);
    }

    @Test
    void INTRO가_아닌_단계에_message가_없으면_INVALID_INPUT이다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);

        BusinessException exception = assertThrows(BusinessException.class, () -> support.chat(USER_ID,
                new RetrospectChatRequest(TRANSACTION_ID, "   ", ReflectionStep.SATISFACTION, null, null)));

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(aiClient);
    }

    @Test
    void 표준_태그_밖_목적은_null로_버리고_되묻는다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("야식이셨군요?", toolResults(reflectionData("야식", null, null, null, List.of())),
                true, false));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "야식 시켰어요", ReflectionStep.PURPOSE,
                new ReflectionDraft(Satisfaction.LOW, null, null, null), null));

        assertNull(response.reflection().purpose());
        // AI가 uncertain_fields에 넣지 않아도 서버가 버린 값은 되물어야 한다. 만족도는 이미 확인됐으므로 다음은 PURPOSE.
        assertTrue(response.uncertainFields().contains("purpose"));
        assertEquals(ReflectionStep.PURPOSE, response.step());
        assertTrue(response.needsClarification());
    }

    @Test
    void 표준_태그_목적은_그대로_살린다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("충동 소비로 보여요.",
                toolResults(reflectionData("충동", "혼자", "LOW", null, List.of())), false, false));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "그냥 시켰어요", ReflectionStep.PURPOSE, null, null));

        assertEquals("충동", response.reflection().purpose());
        assertEquals("혼자", response.reflection().companion());
        assertEquals(Satisfaction.LOW, response.reflection().satisfaction());
        assertFalse(response.needsClarification());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 가운뎃점_둘레_공백은_확정값도_AI_후보값도_정본_표기로_맞춘다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("친구들과 만나셨군요.",
                toolResults(reflectionData("만남 · 사교", null, null, null, List.of())), false, false));

        // 확정값(companion)은 요청이, 후보값(purpose)은 AI가 공백을 넣어 보낸 경우다. 둘 다 400이 아니라 정본으로 맞춘다.
        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "친구 만났어요", ReflectionStep.PURPOSE,
                new ReflectionDraft(Satisfaction.HIGH, null, "친구 ", null), null));

        assertEquals("만남·사교", response.reflection().purpose());
        assertFalse(response.uncertainFields().contains("purpose"));

        // 확정값도 AI에 정본으로 나간다 — 공백이 낀 채 넘기면 AI 프롬프트와 저장 값이 어긋난다.
        Map<String, Object> reflectionState =
                (Map<String, Object>) captureChatRequest().taskContext().state().get("reflection");
        assertEquals("친구", reflectionState.get("companion"));
    }

    @Test
    void repeat_intention은_repeatIntent로_바뀌고_다음_단계는_REPEAT다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("또 하실 건가요?",
                toolResults(reflectionData("충동", "혼자", "LOW", null, List.of("repeat_intention"))), true, false));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "별로였어요", ReflectionStep.COMPANION, null, null));

        assertEquals(List.of("repeatIntent"), response.uncertainFields());
        assertEquals(ReflectionStep.REPEAT, response.step());
    }

    @Test
    void 폴백이라_추출이_없으면_남은_미확정_항목을_계속_묻는다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("이 소비, 만족하셨나요?", List.of(), false, true));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "별로였어요", ReflectionStep.SATISFACTION,
                new ReflectionDraft(Satisfaction.UNKNOWN, null, null, null), null));

        assertEquals(ReflectionStep.SATISFACTION, response.step());
        assertTrue(response.fallback());

        RetrospectChatResponse afterSatisfaction = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "충동이었어요", ReflectionStep.PURPOSE,
                new ReflectionDraft(Satisfaction.LOW, null, null, null), null));
        assertEquals(ReflectionStep.PURPOSE, afterSatisfaction.step());
    }

    @Test
    void 미확정이_없으면_CONFIRM으로_넘어간다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("이렇게 정리할까요?",
                toolResults(reflectionData("충동", "혼자", "LOW", Boolean.FALSE, List.of())), false, false));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "다신 안 시킬래요", ReflectionStep.REPEAT, null, null));

        assertEquals(ReflectionStep.CONFIRM, response.step());
        assertEquals(List.of(), response.uncertainFields());
        assertEquals(Boolean.FALSE, response.reflection().repeatIntent());
    }

    @Test
    void INTRO는_미확정이_없어도_만족도부터_묻는다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.TIMESLOT_OUTLIER);
        givenAiReply(new ChatResponse("이 거래를 골랐어요.", null, null, false));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, null, ReflectionStep.INTRO, null, null));

        assertEquals(ReflectionStep.SATISFACTION, response.step());
    }

    @Test
    void tool_results가_없으면_요청_reflection을_그대로_돌려준다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("네, 알겠어요.", List.of(), null, false));

        ReflectionDraft confirmed = new ReflectionDraft(Satisfaction.HIGH, "식사", "친구", Boolean.TRUE);
        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "맞아요", ReflectionStep.CONFIRM, confirmed, null));

        // 값이 같으면 된다 — 확정값은 표준 태그 정규화를 지나므로 같은 인스턴스가 아니다.
        assertEquals(confirmed, response.reflection());
    }

    @Test
    void recentMessages는_마지막_6개만_AI에_간다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("네.", null, null, false));

        List<ChatMessage> history = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            history.add(new ChatMessage("user", "메시지" + index));
        }

        support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "여덟 번째", ReflectionStep.PURPOSE, null, history));

        List<ChatMessage> sent = captureChatRequest().recentMessages();
        assertEquals(6, sent.size());
        assertEquals("메시지2", sent.get(0).content());
        assertEquals("메시지7", sent.get(5).content());
    }

    @Test
    void 남의_거래는_NOT_FOUND다() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> support.chat(USER_ID,
                new RetrospectChatRequest(TRANSACTION_ID, "안녕", ReflectionStep.INTRO, null, null)));

        assertEquals(CommonErrorCode.NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(aiClient);
    }

    @Test
    void 이미_회고한_거래는_DUPLICATE_RETROSPECT다() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(transaction));
        when(retrospectRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> support.chat(USER_ID,
                new RetrospectChatRequest(TRANSACTION_ID, "안녕", ReflectionStep.INTRO, null, null)));

        assertEquals(CommonErrorCode.DUPLICATE_RETROSPECT, exception.getErrorCode());
        verifyNoInteractions(aiClient);
    }

    @Test
    void 요청의_자유_문자열_목적은_INVALID_TAG다() {
        givenTransactionFound();

        ReflectionDraft confirmed = new ReflectionDraft(Satisfaction.UNKNOWN, "야식", null, null);
        BusinessException exception = assertThrows(BusinessException.class, () -> support.chat(USER_ID,
                new RetrospectChatRequest(TRANSACTION_ID, "야식이요", ReflectionStep.PURPOSE, confirmed, null)));

        assertEquals(CommonErrorCode.INVALID_TAG, exception.getErrorCode());
        verifyNoInteractions(aiClient);
    }

    @Test
    void AI가_LLM_UNAVAILABLE을_던지면_그대로_전파한다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        when(aiClient.chat(any(ChatRequest.class)))
                .thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        BusinessException exception = assertThrows(BusinessException.class, () -> support.chat(USER_ID,
                new RetrospectChatRequest(TRANSACTION_ID, "안녕", ReflectionStep.INTRO, null, null)));

        assertEquals(CommonErrorCode.LLM_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void AI_템플릿_응답이면_fallback을_그대로_내려준다() {
        givenTransactionFound();
        when(candidateService.reasonCodeFor(USER_ID, transaction)).thenReturn(ReasonCode.MANUAL_PICK);
        givenAiReply(new ChatResponse("잠시 후 다시 볼까요?", null, null, true));

        RetrospectChatResponse response = support.chat(USER_ID, new RetrospectChatRequest(
                TRANSACTION_ID, "안녕", ReflectionStep.INTRO, null, null));

        assertTrue(response.fallback());
    }

    private void givenTransactionFound() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(transaction));
        when(retrospectRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false);
    }

    private void givenAiReply(ChatResponse response) {
        when(aiClient.chat(any(ChatRequest.class))).thenReturn(response);
    }

    private ChatRequest captureChatRequest() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        return captor.getValue();
    }

    private List<Map<String, Object>> toolResults(Map<String, Object> data) {
        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("tool_name", "reflection");
        toolResult.put("success", true);
        toolResult.put("data", data);
        return List.of(Map.of("tool_name", "other", "data", Map.of("purpose", "무시")), toolResult);
    }

    private Map<String, Object> reflectionData(String purpose, String companion, String satisfaction,
                                               Boolean repeatIntention, List<String> uncertainFields) {
        Map<String, Object> data = new HashMap<>();
        data.put("purpose", purpose);
        data.put("companion", companion);
        data.put("satisfaction", satisfaction);
        data.put("repeat_intention", repeatIntention);
        data.put("uncertain_fields", uncertainFields);
        return data;
    }
}
