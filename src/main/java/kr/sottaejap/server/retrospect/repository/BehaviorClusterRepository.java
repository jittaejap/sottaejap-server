package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BehaviorClusterRepository extends JpaRepository<BehaviorCluster, Long> {

    List<BehaviorCluster> findAllByUserIdOrderByClusterKeyAsc(long userId);

    Optional<BehaviorCluster> findByUserIdAndClusterKey(long userId, String clusterKey);

    /** 명명 대상 — 커밋 후 displayName이 비어 있는 묶음만 (E-64). */
    List<BehaviorCluster> findAllByUserIdAndDisplayNameIsNullOrderByClusterKeyAsc(long userId);
}
