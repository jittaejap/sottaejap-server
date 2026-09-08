package kr.sottaejap.server.suggestion.repository;

import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * `suggestions`에는 `user_id`가 없다 (04 §1). 사용자별 조회는 전부 `behavior_clusters` 조인이고,
 * <b>"남의 제안"을 막는 지점이 그 조인 조건 하나뿐</b>이다 — 새 조회를 더할 때도 조인을 빼지 않는다.
 */
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    @Query("""
            select s from Suggestion s
            where s.behaviorId in (select c.id from BehaviorCluster c where c.userId = :userId)
            """)
    List<Suggestion> findAllByUserId(@Param("userId") long userId);

    @Query("""
            select s from Suggestion s
            where s.id = :id
              and s.behaviorId in (select c.id from BehaviorCluster c where c.userId = :userId)
            """)
    Optional<Suggestion> findByIdAndUserId(@Param("id") Long id, @Param("userId") long userId);

    /** 재계산 동기화 — 이번 대상 묶음들의 기존 제안을 한 번에 읽는다 (E-81). */
    List<Suggestion> findAllByBehaviorIdIn(Collection<Long> behaviorIds);

    /** `GET /goals`의 `adoptedSaving` 합산 (E-83). */
    List<Suggestion> findAllByGoalIdInAndStatus(Collection<Long> goalIds, SuggestionStatus status);
}
