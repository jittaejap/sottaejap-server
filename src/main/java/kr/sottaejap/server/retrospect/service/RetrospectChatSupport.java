package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.ai.dto.ChatMessage;
import kr.sottaejap.server.ai.dto.ChatRequest;
import kr.sottaejap.server.ai.dto.ChatResponse;
import kr.sottaejap.server.ai.dto.TaskContext;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.ReflectionStep;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.StandardTags;
import kr.sottaejap.server.common.enums.TaskType;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.dto.ReflectionDraft;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * POST /retrospects/chat 프록시 (E-63). 상태 없는 경유지 — 회고 행을 만들지 않고 AI /chat(REFLECTION)에 위임한다.
 *
 * <p>reasonCode는 서버가 ⓪ 규칙으로 구하고(NFR-02), AI가 돌려준 목적·동행인은 표준 태그 밖이면 버린다 (E-20).
 */
@Component
@RequiredArgsConstructor
public class RetrospectChatSupport {

    /** 전체 이력이 아니라 최근 6개만 넘긴다 (E-63 · 05 §2). */
    private static final int RECENT_MESSAGE_LIMIT = 6;

    /** AI는 빈 message를 받지 않는다. INTRO에서 message가 없으면 이 문구로 대신한다 (05 §2). */
    static final String INTRO_MESSAGE = "회고를 시작할게요";

    /** AI 응답의 회고 후보가 담긴 tool_results 항목 이름 (05 §3). */
    private static final String REFLECTION_TOOL_NAME = "reflection";

    private final TransactionRepository transactionRepository;
    private final RetrospectRepository retrospectRepository;
    private final CandidateService candidateService;
    private final AiClient aiClient;

    /**
     * 일부러 @Transactional이 아니다 — AI 왕복(최대 15초) 동안 커넥션을 잡고 있으면 대화 턴마다 풀(기본 10)이 마른다 (E-64).
     * 리포지토리 호출 두 개와 {@link CandidateService#reasonCodeFor}가 각자 짧은 트랜잭션을 쓰고, AI를 부르기 전에
     * 엔티티 접근이 끝나 있어 지연 로딩이 열릴 자리가 없다.
     */
    public RetrospectChatResponse chat(long userId, RetrospectChatRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(request.transactionId(), userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (retrospectRepository.existsByTransactionId(request.transactionId())) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT);
        }

        ReflectionDraft confirmed = request.reflectionOrDefault();
        validateTags(confirmed);

        ReflectionStep step = request.stepOrDefault();
        ReasonCode reasonCode = candidateService.reasonCodeFor(userId, transaction);
        ChatRequest chatRequest = new ChatRequest(
                resolveMessage(request.message(), step),
                String.valueOf(userId),
                new TaskContext(TaskType.REFLECTION, RetrospectStatus.ACTIVE, buildState(transaction, reasonCode, confirmed, step)),
                recentMessages(request.recentMessagesOrEmpty()));

        // 타임아웃·5xx는 AiClient가 이미 503 LLM_UNAVAILABLE로 바꿔 던진다. 여기서 삼키지 않는다.
        ChatResponse response = aiClient.chat(chatRequest);

        Map<String, Object> data = reflectionData(response);
        Set<String> uncertainFields = uncertainFields(data);
        ReflectionDraft candidate = cleanReflection(confirmed, data, uncertainFields);
        List<String> uncertain = List.copyOf(uncertainFields);

        return new RetrospectChatResponse(
                response.reply(),
                nextStep(candidate),
                candidate,
                Boolean.TRUE.equals(response.needsClarification()),
                uncertain,
                response.isFallback());
    }

    /** 사용자가 확인한 값도 표준 태그여야 한다. null은 미확정이므로 허용한다 (E-20). */
    private void validateTags(ReflectionDraft draft) {
        if (draft.purpose() != null && !StandardTags.isPurpose(draft.purpose())) {
            throw new BusinessException(CommonErrorCode.INVALID_TAG);
        }
        if (draft.companion() != null && !StandardTags.isCompanion(draft.companion())) {
            throw new BusinessException(CommonErrorCode.INVALID_TAG);
        }
    }

    private String resolveMessage(String message, ReflectionStep step) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        if (step == ReflectionStep.INTRO) {
            return INTRO_MESSAGE;
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private List<ChatMessage> recentMessages(List<ChatMessage> messages) {
        if (messages.size() <= RECENT_MESSAGE_LIMIT) {
            return messages;
        }
        return List.copyOf(messages.subList(messages.size() - RECENT_MESSAGE_LIMIT, messages.size()));
    }

    /**
     * 05 §3 REFLECTION state. 키는 문서와 글자 단위로 같아야 하고 값이 null일 수 있으므로 LinkedHashMap을 쓴다.
     */
    private Map<String, Object> buildState(Transaction transaction, ReasonCode reasonCode,
                                           ReflectionDraft confirmed, ReflectionStep step) {
        Map<String, Object> transactionState = new LinkedHashMap<>();
        transactionState.put("id", transaction.getId());
        // DB의 OffsetDateTime은 UTC로 정규화돼 있다. AI에는 KST(+09:00) 문자열로 보낸다.
        transactionState.put("occurred_at",
                transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE).toOffsetDateTime().toString());
        transactionState.put("merchant", transaction.getMerchant());
        transactionState.put("amount", transaction.getAmount());
        transactionState.put("category", transaction.getCategory());
        transactionState.put("time_slot", transaction.getTimeSlot().name());

        Map<String, Object> reflectionState = new LinkedHashMap<>();
        reflectionState.put("satisfaction", confirmed.satisfaction() == null ? null : confirmed.satisfaction().name());
        reflectionState.put("purpose", confirmed.purpose());
        reflectionState.put("companion", confirmed.companion());
        reflectionState.put("repeat_intention", confirmed.repeatIntent());

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("transaction", transactionState);
        state.put("reason_code", reasonCode.name());
        state.put("reflection", reflectionState);
        state.put("step", step.name());
        return state;
    }

    /** tool_results 중 tool_name이 reflection인 첫 항목의 data. 없으면 null. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> reflectionData(ChatResponse response) {
        List<Map<String, Object>> toolResults = response.toolResults();
        if (toolResults == null) {
            return null;
        }
        for (Map<String, Object> toolResult : toolResults) {
            if (toolResult != null && REFLECTION_TOOL_NAME.equals(toolResult.get("tool_name"))) {
                Object data = toolResult.get("data");
                return data instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
            }
        }
        return null;
    }

    /** AI의 uncertain_fields를 camelCase로 바꾼다 (repeat_intention → repeatIntent). */
    private Set<String> uncertainFields(Map<String, Object> data) {
        Set<String> fields = new LinkedHashSet<>();
        if (data == null || !(data.get("uncertain_fields") instanceof List<?> raw)) {
            return fields;
        }
        for (Object field : raw) {
            if (field != null) {
                fields.add(toCamelCase(field.toString()));
            }
        }
        return fields;
    }

    private String toCamelCase(String field) {
        return "repeat_intention".equals(field) ? "repeatIntent" : field;
    }

    /**
     * AI 후보값 정화 (E-20). 목적·동행인은 표준 태그일 때만 살리고, 버린 값은 되물어야 하므로 uncertainFields에 넣는다.
     * data에 키가 없는 필드는 사용자가 이미 확인한 값을 유지한다 — 만족도·재구매 의사도 형식이 어긋나면 같다.
     */
    private ReflectionDraft cleanReflection(ReflectionDraft confirmed, Map<String, Object> data,
                                            Set<String> uncertainFields) {
        if (data == null) {
            return confirmed;
        }

        // 키가 없으면 AI가 이번 턴에 판단하지 않은 것 — 사용자가 확인한 값을 유지한다. 키가 있는데 표준 태그 밖이면 버리고 되묻는다.
        String purpose = confirmed.purpose();
        if (data.containsKey("purpose")) {
            Object rawPurpose = data.get("purpose");
            if (rawPurpose == null) {
                purpose = null;
            } else if (StandardTags.isPurpose(rawPurpose.toString())) {
                purpose = rawPurpose.toString();
            } else {
                purpose = null;
                uncertainFields.add("purpose");
            }
        }

        String companion = confirmed.companion();
        if (data.containsKey("companion")) {
            Object rawCompanion = data.get("companion");
            if (rawCompanion == null) {
                companion = null;
            } else if (StandardTags.isCompanion(rawCompanion.toString())) {
                companion = rawCompanion.toString();
            } else {
                companion = null;
                uncertainFields.add("companion");
            }
        }

        Satisfaction satisfaction = parseSatisfaction(data.get("satisfaction"), confirmed.satisfaction());
        Boolean repeatIntent = data.get("repeat_intention") instanceof Boolean value ? value : confirmed.repeatIntent();
        return new ReflectionDraft(satisfaction, purpose, companion, repeatIntent);
    }

    private Satisfaction parseSatisfaction(Object raw, Satisfaction fallback) {
        if (raw == null) {
            return fallback;
        }
        for (Satisfaction satisfaction : Satisfaction.values()) {
            if (satisfaction.name().equals(raw.toString())) {
                return satisfaction;
            }
        }
        return fallback;
    }

    /**
     * 다음 단계 = 정화된 reflection에서 아직 미확정인 첫 항목 (satisfaction UNKNOWN → purpose null → companion null →
     * repeatIntent null 순). 전부 확정이면 CONFIRM. AI가 폴백(템플릿)이라 아무것도 추출하지 못해도 남은 항목을
     * 계속 묻게 된다 — uncertainFields만 보면 폴백에서 CONFIRM으로 건너뛴다 (9/7 실측).
     */
    private ReflectionStep nextStep(ReflectionDraft candidate) {
        if (candidate.satisfaction() == null || candidate.satisfaction() == Satisfaction.UNKNOWN) {
            return ReflectionStep.SATISFACTION;
        }
        if (candidate.purpose() == null) {
            return ReflectionStep.PURPOSE;
        }
        if (candidate.companion() == null) {
            return ReflectionStep.COMPANION;
        }
        if (candidate.repeatIntent() == null) {
            return ReflectionStep.REPEAT;
        }
        return ReflectionStep.CONFIRM;
    }
}
