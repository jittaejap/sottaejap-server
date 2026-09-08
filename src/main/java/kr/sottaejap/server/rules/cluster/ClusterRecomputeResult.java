package kr.sottaejap.server.rules.cluster;

import java.util.List;

/**
 * 전체 재계산 결과.
 *
 * @param userAverage 사용자 전체 평균 (UNKNOWN 제외, 표본 0이면 0) — User.avgSatisfaction 캐시에 쓴다 (E-61)
 * @param clusters    묶음 평가 목록 — 상위 묶음 전체(clusterKey 오름차순) 다음에 리프 전체(clusterKey 오름차순).
 *                    상위 묶음이 자식보다 먼저 와야 upsert가 parentKey를 걸 수 있다
 */
public record ClusterRecomputeResult(double userAverage, List<ClusterEvaluation> clusters) {
}
