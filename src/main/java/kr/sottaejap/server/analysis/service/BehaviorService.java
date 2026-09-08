package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.BehaviorListResponse;

public interface BehaviorService {

    BehaviorListResponse behaviors(long userId);

    BehaviorDetailResponse behavior(long userId, long behaviorId);
}
