package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionListResponse;
import kr.sottaejap.server.suggestion.dto.SuggestionView;

public interface SuggestionService {

    /** 05 §2 #15. {@code status}가 없으면 PROPOSED · ADOPTED만 — 거절한 제안은 기본 목록에 두지 않는다. */
    SuggestionListResponse list(long userId, SuggestionStatus status);

    /** AI `get_action_plan` (05 §3). 외부 기본 목록과 같다. */
    SuggestionListResponse internalList(long userId);

    SuggestionView adopt(long userId, long suggestionId, SuggestionAdoptRequest request);

    SuggestionView reject(long userId, long suggestionId);
}
