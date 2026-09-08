package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.rules.RuleParams;

/**
 * 롤업 판정 (③ · E-59). 리프 묶음의 회고가 적으면 상위 묶음 `카테고리|시간대||`에 붙인다.
 *
 * <p>기준 건수는 `rules.rollup-min-count`다 (액션시트 #16). 저장할 때마다 다시 판정하므로
 * 건수가 차면 리프가 자동으로 분리된다 — 별도 승격 절차가 없다.
 */
public final class RollupRule {

    private RollupRule() {
    }

    /** 리프 회고 수(UNKNOWN 포함)가 `rules.rollup-min-count` 미만이면 상위 묶음에 붙인다 (E-59). */
    public static boolean needsRollup(int leafRetrospectCount, RuleParams params) {
        return leafRetrospectCount < RuleParams.require(params.rollupMinCount(), "rules.rollup-min-count");
    }
}
