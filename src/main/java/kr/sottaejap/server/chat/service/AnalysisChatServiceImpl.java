package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.chat.dto.AnalysisChatRequest;
import kr.sottaejap.server.chat.dto.AnalysisChatResponse;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소비 분석 채널의 자유 질문 한 턴 (05 #28 · E-104 · FR-11-05 · P2). Spring은 기준월과 질문을 넘기기만 한다.
 *
 * <p>집계는 AI가 {@code ANALYSIS} 도구로 내부 {@code /internal/ai/users/{id}/analysis}를 읽는다 — 여기서 집계를
 * 싣거나 답을 판단하지 않는다 (NFR-02). 유효 묶음이 없을 때의 안내문도 AI 핸들러가 돌려주고 서버는 바꾸지 않는다
 * (#24와 같은 원칙).
 *
 * <p>금융 Q&A(#24)와 달리 대화를 저장하지 않는다 — 상태 없는 프록시다 ({@code POST /retrospects/chat}, E-63).
 * {@code chat_messages}의 {@code transaction_id IS NULL}은 금융 Q&A의 맥락(E-67)이라 이 채널을 섞지 않는다.
 *
 * <p>{@code highlight} 캐시(E-102)와 무관하다 — 그 캐시는 {@code ANALYSIS_NARRATE}의 것이고 이것은 다른 Task다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisChatServiceImpl implements AnalysisChatService {

    /** 05 §3 ANALYSIS state의 유일한 키. 문서와 글자 단위로 같아야 한다 (E-24). */
    static final String ANALYSIS_YEAR_MONTH_KEY = "analysis_year_month";

    /** 회고 · 금융 Q&A와 같은 기준 (E-87). */
    private static final int RECENT_MESSAGE_LIMIT = 6;

    private final AiClient aiClient;
    private final TransactionService transactionService;

    /**
     * 일부러 @Transactional이 아니다 — AI 왕복(최대 15초) 동안 커넥션을 잡고 있으면 대화 턴마다 풀이 마른다 (E-64).
     * 기준월 조회는 {@code TransactionService}가 자기 트랜잭션을 짧게 쓰고, 그 뒤로는 DB를 건드리지 않는다.
     */
    @Override
    public AnalysisChatResponse ask(long userId, AnalysisChatRequest request) {
        ChatResponse response = aiClient.chat(new ChatRequest(
                request.message(),
                String.valueOf(userId),
                new TaskContext(TaskType.ANALYSIS, RetrospectStatus.ACTIVE,
                        state(transactionService.analysisYearMonth(userId))),
                recentMessages(request.recentMessagesOrEmpty())));
        return AnalysisChatResponse.from(response);
    }

    /** 기준월은 {@code GET /analysis}와 같은 값이고, 거래가 없으면 null이다 — 값이 null일 수 있어 LinkedHashMap을 쓴다. */
    private static Map<String, Object> state(YearMonth analysisYearMonth) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(ANALYSIS_YEAR_MONTH_KEY, analysisYearMonth == null ? null : analysisYearMonth.toString());
        return state;
    }

    private static List<ChatMessage> recentMessages(List<ChatMessage> messages) {
        if (messages.size() <= RECENT_MESSAGE_LIMIT) {
            return messages;
        }
        return List.copyOf(messages.subList(messages.size() - RECENT_MESSAGE_LIMIT, messages.size()));
    }
}
