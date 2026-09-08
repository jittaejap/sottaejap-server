package kr.sottaejap.server.goal.dto;

import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.rules.saving.GoalProjectionRule;

/**
 * 목표 한 건 (05 §2 · E-83).
 *
 * @param adoptedSaving   이 목표에 붙은 ADOPTED 제안의 expectedSaving 합
 * @param achievementRate 실적 기준 달성률. 채택은 이걸 올리지 않는다 (E-82)
 * @param projectedRate   채택한 제안까지 반영한 전망
 */
public record GoalView(
        Long id,
        String name,
        int targetAmount,
        int currentAmount,
        int adoptedSaving,
        Double achievementRate,
        Double projectedRate
) {

    public static GoalView of(Goal goal, int adoptedSaving) {
        return new GoalView(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                adoptedSaving,
                GoalProjectionRule.achievementRate(goal.getCurrentAmount(), goal.getTargetAmount()),
                GoalProjectionRule.projectedRate(goal.getCurrentAmount(), adoptedSaving, goal.getTargetAmount()));
    }
}
