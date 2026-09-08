package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.chat.dto.FinanceChatRequest;
import kr.sottaejap.server.chat.dto.FinanceChatResponse;

public interface FinanceChatService {

    FinanceChatResponse ask(long userId, FinanceChatRequest request);
}
