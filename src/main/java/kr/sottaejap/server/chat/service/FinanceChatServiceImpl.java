package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.chat.domain.ChatMessage;
import kr.sottaejap.server.chat.dto.FinanceChatRequest;
import kr.sottaejap.server.chat.dto.FinanceChatResponse;
import kr.sottaejap.server.chat.repository.ChatMessageRepository;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.enums.TimeSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 금융 지식 Q&A 한 턴 (05 #24 · FR-12 · P2). Spring은 질문을 넘기고 오간 말을 남기기만 한다.
 *
 * <p>근거 검색(RAG)도 답변 생성도 AI 쪽 일이다. 여기서 금융 지식을 판단하거나 보정하지 않는다 (NFR-02).
 * 근거를 못 찾으면 AI가 "확인할 수 없다"고 답한다 (FR-12-02) — Spring이 그것을 다른 문장으로 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FinanceChatServiceImpl implements FinanceChatService {

    /** 회고와 같은 기준. "그럼 그건 얼마예요?" 같은 되물음이 이어지려면 직전 몇 턴은 있어야 한다. */
    private static final int RECENT_MESSAGE_LIMIT = 6;

    private final AiClient aiClient;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 일부러 @Transactional이 아니다 — AI 왕복(최대 15초) 동안 커넥션을 잡고 있으면 대화 턴마다 풀(기본 10)이
     * 마른다 (E-64). {@link kr.sottaejap.server.retrospect.service.RetrospectChatSupport#chat}과 같은 이유다.
     * 최근 대화는 AI를 부르기 전에 리스트로 다 꺼내 놓아 지연 로딩이 열릴 자리가 없고, 저장은 {@code saveAll}
     * 한 번이 자기 트랜잭션을 짧게 쓴다.
     */
    @Override
    public FinanceChatResponse ask(long userId, FinanceChatRequest request) {
        ChatResponse response = aiClient.chat(new ChatRequest(
                request.message(),
                String.valueOf(userId),
                // state는 빈 객체다 — 질문은 message, 맥락은 recent_messages에 있다 (E-47).
                new TaskContext(TaskType.FINANCE_QA, RetrospectStatus.ACTIVE, Map.of()),
                recentMessages(userId)));

        record(userId, request.message(), response.reply());
        return FinanceChatResponse.from(response);
    }

    private List<kr.sottaejap.server.ai.dto.ChatMessage> recentMessages(long userId) {
        List<ChatMessage> latestFirst = chatMessageRepository
                .findByUserIdAndTransactionIdIsNullOrderByCreatedAtDesc(
                        userId, PageRequest.of(0, RECENT_MESSAGE_LIMIT));
        List<kr.sottaejap.server.ai.dto.ChatMessage> messages = new ArrayList<>(latestFirst.size());
        for (ChatMessage stored : latestFirst) {
            messages.add(new kr.sottaejap.server.ai.dto.ChatMessage(
                    stored.getRole().toAiRole(), stored.getContent()));
        }
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 거래에 매이지 않는 대화라 transactionId는 null이다.
     *
     * <p>두 행을 한 번에 저장한다 — save 두 번이면 트랜잭션도 둘이라 답변만 빠진 대화가 남을 수 있다.
     */
    private void record(long userId, String question, String reply) {
        OffsetDateTime now = OffsetDateTime.now(TimeSlot.ZONE);
        chatMessageRepository.saveAll(List.of(
                ChatMessage.of(userId, null, ChatMessage.Role.USER, question, now),
                ChatMessage.of(userId, null, ChatMessage.Role.ASSISTANT, reply, now)));
    }
}
