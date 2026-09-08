package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.transaction.dto.TransactionAiView;

import java.util.List;

/**
 * 묶음 상세 (05 §2 `GET /behaviors/{id}` · FR-07-05).
 *
 * <p>거래는 이 묶음과 자식 묶음에 배정된 것의 합집합이다 — 상위 묶음에는 직접 구성원만 배정돼 있어
 * 자식을 함께 읽지 않으면 상세가 비어 보인다 (E-59).
 */
public record BehaviorDetailResponse(BehaviorView behavior, List<TransactionAiView> transactions) {
}
