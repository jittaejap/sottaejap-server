package kr.sottaejap.server.suggestion.dto;

import java.util.List;

/** 제안 목록 (05 §2 #15). 내부 AI `get_action_plan`도 같은 모양이다 (05 §3). */
public record SuggestionListResponse(List<SuggestionView> suggestions) {
}
