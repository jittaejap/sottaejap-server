package kr.sottaejap.server.analysis.dto;

import java.util.List;

/** 묶음 목록 (05 §2 `GET /behaviors`). 지도와 같은 순서다 — 같은 것을 두 방식으로 보여 주는 화면이라서. */
public record BehaviorListResponse(List<BehaviorView> behaviors) {
}
