package kr.sottaejap.server.rules.saving;

/**
 * 절감액 (⑦ · 04 §3 · E-82). 순수 함수다 — 같은 단가와 횟수면 항상 같은 금액이다 (NFR-01).
 *
 * <p>튜닝값이 없다. 아래 {@link #MIN_ADJUST_COUNT}는 규칙 파라미터가 아니라 <b>구조 상수</b>다 —
 * "0번 줄이겠다"는 채택은 채택이 아니므로 값을 바꿀 여지가 없다.
 */
public final class SavingRule {

    /** 조정 횟수 하한. 0을 허용하면 절감액 0원짜리 채택이 목표에 붙는다. */
    public static final int MIN_ADJUST_COUNT = 1;

    private SavingRule() {
    }

    /** {@code avgAmount × adjustCount} (04 §3). 채택 시점의 단가로 굳는다 — 이후 재계산이 바꾸지 않는다 (E-81). */
    public static int expectedSaving(int avgAmount, int adjustCount) {
        return avgAmount * adjustCount;
    }

    /**
     * 조정 횟수는 그 묶음의 이번 달 거래 건수를 넘을 수 없다 (FR-08-02).
     * 8번 쓴 소비를 10번 줄이겠다는 계획은 세울 수 없다.
     */
    public static boolean isValidAdjustCount(int adjustCount, int txCount) {
        return adjustCount >= MIN_ADJUST_COUNT && adjustCount <= txCount;
    }
}
