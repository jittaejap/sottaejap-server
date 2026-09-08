package kr.sottaejap.server.report.repository;

import kr.sottaejap.server.report.domain.MonthlySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MonthlySnapshotRepository extends JpaRepository<MonthlySnapshot, Long> {

    /** `uq_monthly_snapshots(user_id, year_month)` — 사용자 · 달에 행은 하나다. `yearMonth`는 `YYYY-MM`이다. */
    Optional<MonthlySnapshot> findByUserIdAndYearMonth(long userId, String yearMonth);

    /**
     * 지난달 확정 — 행이 없을 때만 넣는다 (E-94 ③). 돌려주는 값은 넣은 행 수(0 또는 1)다.
     *
     * <p>{@code save}가 아니라 {@code ON CONFLICT DO NOTHING}인 이유는 <b>같은 달의 첫 조회가 동시에 둘 들어올 때</b>다
     * (화면이 두 번 마운트되거나 탭이 둘). 확정과 목표 배분은 한 트랜잭션이어야 하는데(04 §3), JPA insert로 두면
     * 진 쪽이 유일 제약 예외로 트랜잭션째 죽어 500이 된다. 이 문장은 진 쪽에 0을 돌려주고, 부르는 쪽은 0이면
     * 배분하지 않고 이긴 쪽이 저장한 행을 읽어 돌려준다 — 배분은 정확히 한 번이다.
     *
     * <p>{@code savedAmount}는 전월이 없으면 null이다. null 파라미터는 cast로 타입을 먼저 알려 준다 — 맨 파라미터로
     * 보내면 PostgreSQL이 "could not determine data type of parameter"로 거절한다.
     */
    @Modifying
    @Query(value = """
            insert into monthly_snapshots (user_id, year_month, total_spending, unsatisfied_count, repeat_count, saved_amount)
            values (:userId, :yearMonth, :totalSpending, :unsatisfiedCount, :repeatCount, cast(:savedAmount as integer))
            on conflict (user_id, year_month) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") long userId,
                       @Param("yearMonth") String yearMonth,
                       @Param("totalSpending") int totalSpending,
                       @Param("unsatisfiedCount") int unsatisfiedCount,
                       @Param("repeatCount") int repeatCount,
                       @Param("savedAmount") Integer savedAmount);
}
