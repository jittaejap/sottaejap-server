package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.retrospect.domain.Retrospect;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RetrospectRepository extends JpaRepository<Retrospect, Long> {

    boolean existsByTransactionId(Long transactionId);

    Optional<Retrospect> findByTransactionId(Long transactionId);

    /** 월간 리포트 — 그 달 거래들의 회고를 한 번에 (E-94 `unsatisfiedCount`). */
    List<Retrospect> findAllByTransactionIdIn(Collection<Long> transactionIds);

    /** 재계산 입력 — 사용자의 회고 전부와 거래를 한 번에 (E-61). */
    @Query("""
            select new kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction(r, t)
            from Retrospect r join Transaction t on t.id = r.transactionId
            where t.userId = :userId
            order by r.createdAt desc, r.id desc
            """)
    List<RetrospectWithTransaction> findAllWithTransactionByUserId(@Param("userId") long userId);

    /** 내부 AI `get_memory`의 recentReflections — 최신 N건. */
    @Query("""
            select new kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction(r, t)
            from Retrospect r join Transaction t on t.id = r.transactionId
            where t.userId = :userId
            order by r.createdAt desc, r.id desc
            """)
    List<RetrospectWithTransaction> findRecentWithTransactionByUserId(@Param("userId") long userId, Pageable pageable);
}
