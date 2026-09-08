package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.transaction.domain.Transaction;

import java.time.LocalDate;
import java.util.List;

/** 회고 후보 ⓪ (E-62). "오늘"은 Clock으로 보고 규칙에는 금액 목록만 넘긴다. */
public interface CandidateService {

    /**
     * @param limit 1~100 (기본 1은 컨트롤러가 채운다)
     * @param from  조회 시작일(KST) 또는 null — 없으면 상한 없음 (E-48)
     * @param to    조회 종료일(KST) 또는 null — D+1 컷오프와 더 이른 쪽
     */
    List<CandidateView> findCandidates(long userId, int limit, LocalDate from, LocalDate to);

    /** 거래 한 건의 선정 사유. 어느 규칙에도 맞지 않으면 MANUAL_PICK (E-63 chat용). */
    ReasonCode reasonCodeFor(long userId, Transaction transaction);
}
