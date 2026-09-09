package kr.sottaejap.server.chat.service;

import kr.sottaejap.server.chat.dto.AnalysisChatRequest;
import kr.sottaejap.server.chat.dto.AnalysisChatResponse;

public interface AnalysisChatService {

    AnalysisChatResponse ask(long userId, AnalysisChatRequest request);
}
