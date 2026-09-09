package kr.sottaejap.server.goal.service;

import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.goal.dto.GoalListResponse;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 목표 4종 (05 §2 #4 · #5 · #5a · E-83). */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final SuggestionRepository suggestionRepository;
    private final Clock clock;

    /**
     * 목표마다 채택한 제안의 예상 절감액을 합산한다 (E-83). 목표 하나씩 세면 목표 수만큼 질의가 되므로
     * 한 번에 읽어 메모리에서 묶는다.
     */
    @Override
    @Transactional(readOnly = true)
    public GoalListResponse list(long userId) {
        List<Goal> goals = goalRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId);
        Map<Long, Integer> adoptedSavings = adoptedSavingsByGoal(goals);

        return new GoalListResponse(goals.stream()
                .map(goal -> GoalView.of(goal, adoptedSavings.getOrDefault(goal.getId(), 0)))
                .toList());
    }

    @Override
    @Transactional
    public GoalView create(long userId, GoalRequest request) {
        Goal goal = goalRepository.save(
                Goal.create(userId, request.name(), request.targetAmount(), request.targetDate(), request.currentAmount()));
        return GoalView.of(goal, 0);
    }

    @Override
    @Transactional
    public GoalView update(long userId, long goalId, GoalRequest request) {
        Goal goal = find(userId, goalId);
        goal.update(request.name(), request.targetAmount(), request.targetDate(), request.currentAmount());
        return GoalView.of(goal, adoptedSavingsByGoal(List.of(goal)).getOrDefault(goalId, 0));
    }

    /** soft delete다 (FR-01-02). `suggestions.goal_id`는 그대로 둔다 — 채택 이력을 잃지 않는다 (E-83). */
    @Override
    @Transactional
    public void delete(long userId, long goalId) {
        find(userId, goalId).delete(OffsetDateTime.now(clock));
    }

    private Goal find(long userId, long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private Map<Long, Integer> adoptedSavingsByGoal(List<Goal> goals) {
        List<Long> goalIds = goals.stream().map(Goal::getId).toList();
        Map<Long, Integer> totals = new HashMap<>();
        if (goalIds.isEmpty()) {
            return totals;
        }
        for (Suggestion adopted : suggestionRepository.findAllByGoalIdInAndStatus(goalIds, SuggestionStatus.ADOPTED)) {
            int saving = adopted.getExpectedSaving() == null ? 0 : adopted.getExpectedSaving();
            totals.merge(adopted.getGoalId(), saving, Integer::sum);
        }
        return totals;
    }
}
