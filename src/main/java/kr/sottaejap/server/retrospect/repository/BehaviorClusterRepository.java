package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BehaviorClusterRepository extends JpaRepository<BehaviorCluster, Long> {

    List<BehaviorCluster> findAllByUserIdOrderByClusterKeyAsc(long userId);

    Optional<BehaviorCluster> findByUserIdAndClusterKey(long userId, String clusterKey);

    /** 명명 대상 — 커밋 후 displayName이 비어 있는 묶음만 (E-64). */
    List<BehaviorCluster> findAllByUserIdAndDisplayNameIsNullOrderByClusterKeyAsc(long userId);

    /**
     * 유효 묶음 — 지도 · 분석 · 제안이 함께 보는 대상 (E-72). 회고가 붙지 않은 묶음과 롤업된 리프를 뺀다.
     *
     * <p>필터를 부르는 쪽마다 두지 않고 조회 하나로 고정한다. 한쪽만 고치면 상위 묶음이 이미 들고 있는
     * 금액을 리프가 한 번 더 세게 되고, 그건 화면에서 눈에 띄지 않는다.
     */
    @Query("""
            select c from BehaviorCluster c
            where c.userId = :userId and c.retrospectCount > 0 and c.parentId is null
            order by c.clusterKey asc
            """)
    List<BehaviorCluster> findEffectiveByUserId(@Param("userId") long userId);

    /** 묶음 상세 — 남의 묶음과 없는 묶음을 구별해 주지 않는다. */
    Optional<BehaviorCluster> findByIdAndUserId(Long id, long userId);

    /** 상세의 거래 합집합용 — 이 상위 묶음에 롤업된 리프들 (E-59). */
    List<BehaviorCluster> findAllByParentId(Long parentId);
}
