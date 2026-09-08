package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;

/**
 * 규칙 계층이 보는 묶음 한 건 — 04 BehaviorCluster에서 엔티티를 걷어낸 값이다 (E-73).
 * 집계·정렬 규칙이 JPA를 모르게 하려고 두며, 매핑은 {@code analysis/service/ClusterSnapshotMapper}가 한다.
 *
 * @param clusterKey `카테고리|시간대|목적|동행인` (E-58 · E-59) — 카테고리·시간대의 유일한 출처다
 */
public record ClusterSnapshot(
        Long id,
        String clusterKey,
        String displayName,
        Long parentId,
        int retrospectCount,
        Double adjustedSatisfaction,
        Integer avgAmount,
        int monthlyTotalAmount,
        int txCount,
        Double burdenRatio,
        EvaluationStatus evaluationStatus,
        Quadrant quadrant,
        Verdict verdict
) {

    /** 묶음 키 자리 수 — 카테고리 · 시간대 · 목적 · 동행인 (E-59). */
    private static final int KEY_PARTS = 4;

    public ClusterSnapshot {
        if (clusterKey == null || clusterKey.split("\\|", -1).length != KEY_PARTS) {
            throw new IllegalArgumentException("묶음 키는 네 자리여야 합니다: " + clusterKey);
        }
    }

    /** 키 첫 자리. 미분류는 `기타`로 이미 정규화돼 있다 (E-58). */
    public String category() {
        return clusterKey.split("\\|", -1)[0];
    }

    /** 키 둘째 자리. 식사 계열·`기타`가 아니면 비어 있다 (E-58) — 그때는 null이 정상이다. */
    public TimeSlot timeSlot() {
        String part = clusterKey.split("\\|", -1)[1];
        return part.isBlank() ? null : TimeSlot.valueOf(part);
    }

    /**
     * 지도 · 분석 · 제안의 공통 대상 (E-72). 회고가 붙지 않은 묶음은 화면에 없고, 롤업된 리프는
     * 상위 묶음이 이미 그 금액을 들고 있어 두 번 세게 된다.
     */
    public boolean isEffective() {
        return retrospectCount > 0 && parentId == null;
    }
}
