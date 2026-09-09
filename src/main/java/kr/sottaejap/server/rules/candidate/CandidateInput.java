package kr.sottaejap.server.rules.candidate;

import java.util.List;

/**
 * 거래 한 건의 선별 입력. 서비스가 D+1·회고 제외·기준선 기간을 쿼리로 걸러 만든다.
 *
 * @param amount                          이 거래 금액
 * @param baselineSameSlot                같은 (카테고리, 시간대)의 기준선 금액들 — 이 거래 자신은 뺀다
 * @param baselineSameCategory            같은 카테고리 전체의 기준선 금액들 — 시간대 표본이 부족할 때
 * @param outlierMultiplier               User.outlierThreshold, 없으면 서비스가 sensitivity.standard를 넣는다
 * @param monthlyBudget                   월 예산. 기준 금액이 없을 때의 폴백이고, 둘 다 없으면 THRESHOLD_EXCEEDED 미적용
 * @param outlierBaseAmount               User.outlierBaseAmount — 큰 금액 기준(원). 있으면 예산 비율보다 먼저 본다 (E-115)
 * @param lowSatisfactionCountInParentKey 같은 상위 키(`카테고리|시간대`) 아래 LOW 회고 수
 */
public record CandidateInput(
        int amount,
        List<Integer> baselineSameSlot,
        List<Integer> baselineSameCategory,
        Double outlierMultiplier,
        Integer monthlyBudget,
        Integer outlierBaseAmount,
        int lowSatisfactionCountInParentKey
) {
}
