package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.retrospect.domain.BehaviorCluster;

import java.util.List;

/** 사용자 전체 묶음 재계산 (E-61). 회고 저장 트랜잭션 안에서 부른다. */
public interface ClusterRecomputeService {

    /**
     * 회고 + 거래를 읽어 {@code ClusterEngine.recompute}를 돌리고 (userId, clusterKey)로 upsert한다.
     * 상위 묶음을 먼저 저장해 리프의 parentId를 채우고, 거래의 behaviorId와 User.avgSatisfaction을 갱신한다.
     *
     * @return 이번 계산에 나온 묶음 전부 (저장된 엔티티)
     */
    List<BehaviorCluster> recomputeAll(long userId);
}
