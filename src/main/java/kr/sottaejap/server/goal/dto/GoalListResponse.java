package kr.sottaejap.server.goal.dto;

import java.util.List;

/** 목표 목록 (05 §2 #4). */
public record GoalListResponse(List<GoalView> goals) {
}
