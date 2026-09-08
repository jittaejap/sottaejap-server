package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.transaction.domain.Transaction;

/** 회고 + 그 거래. retrospects에 user_id가 없어 사용자별 조회는 거래를 조인한다 (04 §2). */
public record RetrospectWithTransaction(Retrospect retrospect, Transaction transaction) {
}
