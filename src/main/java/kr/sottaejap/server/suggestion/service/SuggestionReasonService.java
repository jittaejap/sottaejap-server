package kr.sottaejap.server.suggestion.service;

/** 제안 이유 문장(⑦)을 AI에게 받아 채운다 (05 §3 ACTION_PLAN · FR-08-01). */
public interface SuggestionReasonService {

    /**
     * 그 리프의 열린 제안에 이유 문장이 없으면 AI에게 받아 채운다. <b>저장 트랜잭션이 커밋된 뒤</b> 부른다 —
     * AI가 {@code /internal/ai/…/suggestions}로 제안을 되읽으므로 커밋 전이면 방금 만든 제안이 보이지 않는다.
     *
     * <p><b>비동기다 — 호출 즉시 돌아온다.</b> 이유는 회고 저장 응답에 실리지 않아 기다릴 값이 없고,
     * 기다리면 저장 응답이 최악 30초가 되어 클라이언트 타임아웃(20초)에 걸린다 (PR #46 리뷰 2).
     * 실패는 예외로 올라오지 않고 로그로 남는다.
     *
     * @param leafId {@code RetrospectWriter.write}가 돌려준 <b>리프</b> 묶음 id. 리프가 롤업돼 있으면
     *               구현이 상위 묶음을 따라가 그쪽 제안을 채운다 (E-59 · E-72).
     */
    void explainProposed(long userId, long leafId);
}
