package kr.sottaejap.server.goal.service;

import kr.sottaejap.server.goal.dto.GoalListResponse;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;

public interface GoalService {

    GoalListResponse list(long userId);

    GoalView create(long userId, GoalRequest request);

    GoalView update(long userId, long goalId, GoalRequest request);

    void delete(long userId, long goalId);
}
