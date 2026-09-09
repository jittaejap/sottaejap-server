package kr.sottaejap.server.suggestion.service;

/** 제안 이유 문장(⑦)을 AI에게 받아 채운다 (05 §3 ACTION_PLAN · FR-08-01). */
public interface SuggestionReasonService {

    /**
     * 그 묶음의 열린 제안에 이유 문장이 없으면 AI에게 받아 채운다. <b>저장 트랜잭션이 커밋된 뒤</b> 부른다 —
     * AI가 {@code /internal/ai/…/suggestions}로 제안을 되읽으므로 커밋 전이면 방금 만든 제안이 보이지 않는다.
     *
     * <p>AI가 없거나 빈 문장을 주면 아무것도 저장하지 않는다. 화면은 템플릿 문구로 뜬다 (E-38).
     */
    void explainProposed(long userId, long behaviorId);
}
