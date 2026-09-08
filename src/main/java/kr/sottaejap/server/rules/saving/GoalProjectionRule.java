package kr.sottaejap.server.rules.saving;

/**
 * 목표 달성률 (⑦ · E-83). 화면(S13 · FR-08-04)이 "지금"과 "채택대로 가면"을 나란히 보여준다.
 *
 * <p>raw double을 낸다 — 반올림은 화면이 한다. 서버가 반올림하면 두 비율의 차이가 사라져
 * 막대가 안 움직이는 것처럼 보인다.
 */
public final class GoalProjectionRule {

    private GoalProjectionRule() {
    }

    /** 목표 금액이 없거나 0 이하면 비율이 성립하지 않는다 — 0으로 나누는 대신 null이다. */
    public static Double achievementRate(int currentAmount, int targetAmount) {
        return targetAmount <= 0 ? null : (double) currentAmount / targetAmount;
    }

    /** 채택한 제안의 예상 절감액까지 더했을 때의 달성률. 실적이 아니라 전망이다 (E-82). */
    public static Double projectedRate(int currentAmount, int adoptedSaving, int targetAmount) {
        return targetAmount <= 0 ? null : (double) (currentAmount + adoptedSaving) / targetAmount;
    }
}
