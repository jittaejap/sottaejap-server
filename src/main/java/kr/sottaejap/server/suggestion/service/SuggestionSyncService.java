package kr.sottaejap.server.suggestion.service;

public interface SuggestionSyncService {

    /**
     * 제안을 묶음 재계산 결과에 맞춘다 (E-81). {@code recomputeAll} 끝에서 부른다 —
     * 스케줄러도 배치도 두지 않으므로 "지도에 보이는 것 = 제안 목록"이 항상 맞는다.
     */
    void sync(long userId);
}
