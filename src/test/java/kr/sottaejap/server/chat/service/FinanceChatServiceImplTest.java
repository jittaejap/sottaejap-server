package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.chat.domain.ChatMessage;
import kr.sottaejap.server.chat.dto.FinanceChatRequest;
import kr.sottaejap.server.chat.dto.FinanceChatResponse;
import kr.sottaejap.server.chat.repository.ChatMessageRepository;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.enums.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 금융 Q&A는 판단을 AI에 맡긴다. 확인할 것은 무엇을 실어 보내고 무엇을 남기는지다.
 */
class FinanceChatServiceImplTest {

    private static final long USER_ID = 1L;

    /** 저장 시각은 주입한 Clock에서 온다 — 정적 호출이면 테스트에서 고정할 수 없다. */
    private static final OffsetDateTime NOW = LocalDate.of(2026, 9, 7)
            .atTime(LocalTime.of(20, 0)).atZone(TimeSlot.ZONE).toOffsetDateTime();

    private AiClient aiClient;
    private ChatMessageRepository chatMessageRepository;
    private FinanceChatServiceImpl service;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        service = new FinanceChatServiceImpl(aiClient, chatMessageRepository,
                Clock.fixed(NOW.toInstant(), TimeSlot.ZONE));

        when(chatMessageRepository.findByUserIdAndTransactionIdIsNullOrderByCreatedAtDescIdDesc(
                anyLong(), any(Pageable.class))).thenReturn(List.of());
        when(aiClient.chat(any())).thenReturn(new ChatResponse("연금저축 세액공제는 …", List.of(), false, false));
    }

    @Test
    void sendsFinanceQaWithEmptyState() {
        service.ask(USER_ID, new FinanceChatRequest("연금저축 세액공제가 뭐예요?"));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        ChatRequest sent = captor.getValue();
        assertThat(sent.taskContext().task()).isEqualTo(TaskType.FINANCE_QA);
        // 질문은 message에, 맥락은 recent_messages에 있다. state는 비어 있어야 한다 (E-47).
        assertThat(sent.taskContext().state()).isEmpty();
        assertThat(sent.message()).isEqualTo("연금저축 세액공제가 뭐예요?");
    }

    @Test
    void storesTurnWithoutATransaction() {
        service.ask(USER_ID, new FinanceChatRequest("IRP는 뭔가요?"));

        // 질문과 답변은 한 번에 저장한다 — save 두 번이면 답변만 빠진 대화가 남을 수 있다.
        ArgumentCaptor<List<ChatMessage>> saved = ArgumentCaptor.captor();
        verify(chatMessageRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(ChatMessage::getTransactionId)
                .containsOnlyNulls();
        assertThat(saved.getValue())
                .extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
        assertThat(saved.getValue())
                .extracting(ChatMessage::getCreatedAt)
                .containsOnly(NOW);
    }

    @Test
    void sendsRecentMessagesOldestFirst() {
        when(chatMessageRepository.findByUserIdAndTransactionIdIsNullOrderByCreatedAtDescIdDesc(
                anyLong(), any(Pageable.class))).thenReturn(List.of(
                ChatMessage.of(USER_ID, null, ChatMessage.Role.ASSISTANT, "IRP는 …", OffsetDateTime.now()),
                ChatMessage.of(USER_ID, null, ChatMessage.Role.USER, "IRP는 뭔가요?", OffsetDateTime.now())));

        service.ask(USER_ID, new FinanceChatRequest("그럼 한도는요?"));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().recentMessages())
                .extracting(kr.sottaejap.server.ai.dto.ChatMessage::content)
                .containsExactly("IRP는 뭔가요?", "IRP는 …");
    }

    @Test
    void passesFallbackThrough() {
        when(aiClient.chat(any())).thenReturn(new ChatResponse("지금은 답하기 어려워요", List.of(), false, true));

        FinanceChatResponse response = service.ask(USER_ID, new FinanceChatRequest("환율이 뭐예요?"));

        assertThat(response.fallback()).isTrue();
        assertThat(response.reply()).isEqualTo("지금은 답하기 어려워요");
    }
}
