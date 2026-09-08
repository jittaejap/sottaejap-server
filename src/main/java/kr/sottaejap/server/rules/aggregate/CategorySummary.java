package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;

/**
 * 카테고리별 집계 한 행 (E-73 · 05 §2 `byCategory`).
 *
 * @param dominantTimeSlot 월 합계가 가장 큰 시간대. 시간대를 키에 넣지 않는 카테고리는 null이다 (E-58)
 * @param avgAmount        월 합계 ÷ 거래 건수. 건수가 0이면 null
 * @param verdict          월 합계가 가장 큰 RESOLVED 묶음의 판정. 전부 보류면 null
 */
public record CategorySummary(String category,
                              TimeSlot dominantTimeSlot,
                              Integer avgAmount,
                              int monthlyTotalAmount,
                              Verdict verdict) {
}
