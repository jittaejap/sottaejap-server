package kr.sottaejap.server.goal.repository;

import kr.sottaejap.server.goal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /**
     * 실적 반영 (E-94 ④) — <b>DB에서 원자적으로 더한다.</b> 엔티티를 읽어 더하고 쓰면, 서로 다른 달을 동시에
     * 확정하는 두 트랜잭션이 같은 목표 행을 각자 읽고 각자 써서 앞의 배분이 증발한다(lost update — {@code Goal}에
     * {@code @Version}이 없고 READ COMMITTED라 뒤 UPDATE가 그대로 이긴다). 돌려주는 값은 갱신한 행 수다.
     *
     * <p>실행 뒤 영속성 컨텍스트를 비운다 — 같은 트랜잭션이 {@code GoalService.list}로 이미 올려 둔 {@code Goal}이
     * 옛 {@code currentAmount}를 들고 있지 않게. 비우기 전에 미반영 변경은 flush한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Goal g set g.currentAmount = g.currentAmount + :amount where g.id = :id")
    int addCurrentAmount(@Param("id") long id, @Param("amount") int amount);

    /** 삭제한 목표는 목록에서 뺀다 (E-83). 부분 인덱스 `ix_goals_user`가 이 조건을 탄다. */
    List<Goal> findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(long userId);

    /** 남의 목표와 없는 목표를 구별해 주지 않는다 — 둘 다 404다. */
    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(Long id, long userId);
}
