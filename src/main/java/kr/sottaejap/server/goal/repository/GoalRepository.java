package kr.sottaejap.server.goal.repository;

import kr.sottaejap.server.goal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** 삭제한 목표는 목록에서 뺀다 (E-83). 부분 인덱스 `ix_goals_user`가 이 조건을 탄다. */
    List<Goal> findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(long userId);

    /** 남의 목표와 없는 목표를 구별해 주지 않는다 — 둘 다 404다. */
    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(Long id, long userId);
}
