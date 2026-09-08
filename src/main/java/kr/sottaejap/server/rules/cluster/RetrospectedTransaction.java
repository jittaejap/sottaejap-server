package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;

import java.time.YearMonth;

/**
 * 회고가 붙은 거래 하나 — 규칙 엔진 입력. 서비스가 엔티티에서 만들어 넘긴다 (JPA 무관).
 *
 * @param transactionId 거래 id (결과의 behaviorId 배정에 쓴다)
 * @param merchant      가맹점 원본 — 묶음 명명 표본용
 * @param category      내부 카테고리 (지금은 원본 그대로, 없으면 `기타`)
 * @param timeSlot      4구간
 * @param amount        원
 * @param occurredMonth 거래 월 (KST) — analysisYearMonth와 비교
 * @param purpose       사용자가 확인한 목적 태그 또는 null
 * @param companion     사용자가 확인한 동행인 태그 또는 null
 * @param satisfaction  HIGH · LOW · UNKNOWN
 */
public record RetrospectedTransaction(
        long transactionId,
        String merchant,
        String category,
        TimeSlot timeSlot,
        int amount,
        YearMonth occurredMonth,
        String purpose,
        String companion,
        Satisfaction satisfaction
) {
}
