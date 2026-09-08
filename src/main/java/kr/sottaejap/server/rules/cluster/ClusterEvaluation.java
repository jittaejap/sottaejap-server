package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;

import java.time.YearMonth;
import java.util.List;

/**
 * 묶음 하나의 산출값 — 04 BehaviorCluster 컬럼과 1:1. 서비스가 (userId, clusterKey)로 upsert한다.
 *
 * @param clusterKey           `카테고리|시간대|목적|동행인` (리프) 또는 `카테고리|시간대||` (상위)
 * @param parentKey            리프가 롤업 대상이면 상위 키, 아니면 null. 상위 묶음은 항상 null (E-59)
 * @param retrospectCount      UNKNOWN 포함 회고 수
 * @param rawAverage           UNKNOWN 제외 평균, 표본 0이면 null (E-61)
 * @param adjustedSatisfaction 축소 추정 결과 [-1, 1]
 * @param avgAmount            monthlyTotalAmount ÷ txCount, txCount 0이면 null
 * @param monthlyTotalAmount   분석 기준월 합계 (없으면 0)
 * @param analysisYearMonth    어느 달 합계인지
 * @param txCount              기준월 거래 건수
 * @param burdenRatio          monthlyTotalAmount ÷ monthlyBudget, 예산 없으면 null
 * @param evaluationStatus     retrospectCount < pending-min-count 이면 PENDING
 * @param quadrant             PENDING 또는 burdenRatio null이면 null
 * @param verdict              PENDING이면 null
 * @param transactionIds       이 묶음에 <b>직접</b> 속한 거래 id — behaviorId를 배정할 대상이다.
 *                             롤업으로 자식이 붙은 상위 묶음은 비어 있다. 자식 거래의 behaviorId는
 *                             리프에 그대로 남는다 (E-59). 집계(retrospectCount · monthlyTotalAmount)만 합집합이다
 * @param sampleMerchants      묶음 명명용 가맹점 표본, 중복 제거 최대 3개
 */
public record ClusterEvaluation(
        String clusterKey,
        String parentKey,
        int retrospectCount,
        Double rawAverage,
        double adjustedSatisfaction,
        Integer avgAmount,
        int monthlyTotalAmount,
        YearMonth analysisYearMonth,
        int txCount,
        Double burdenRatio,
        EvaluationStatus evaluationStatus,
        Quadrant quadrant,
        Verdict verdict,
        List<Long> transactionIds,
        List<String> sampleMerchants
) {
}
