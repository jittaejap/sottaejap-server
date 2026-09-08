package kr.sottaejap.server.transaction.repository;

import kr.sottaejap.server.transaction.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * 중복 판별용 해시를 한 번에 읽는다. 행마다 exists를 부르면 1,000건 업로드가 1,000질의가 된다 (NFR-03).
     */
    @Query("select t.importHash from Transaction t where t.userId = :userId")
    List<String> findImportHashesByUserId(@Param("userId") long userId);

    /**
     * 선택 조건은 cast로 타입을 먼저 알려 준다. 맨 파라미터로 `:from is null`을 쓰면 PostgreSQL이
     * "could not determine data type of parameter"로 거절한다.
     */
    @Query("""
            select t from Transaction t
            where t.userId = :userId
              and (cast(:from as Timestamp) is null or t.occurredAt >= :from)
              and (cast(:to as Timestamp) is null or t.occurredAt < :to)
              and (cast(:category as String) is null or t.category = :category)
            order by t.occurredAt desc
            """)
    List<Transaction> search(@Param("userId") long userId,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to,
                             @Param("category") String category,
                             Pageable pageable);

    Optional<Transaction> findByIdAndUserId(Long id, long userId);

    /** analysisYearMonth = 최근 거래월 (E-60). */
    Optional<Transaction> findTopByUserIdOrderByOccurredAtDesc(long userId);

    /**
     * 회고 후보 ⓪ — 이미 회고된 거래를 빼고, D+1 컷오프(toExclusive) 이전 거래를 최신순으로 (E-62 ①②).
     * from은 채팅 3일 창·날짜 지정용이며 없으면 상한 없이 본다 (E-48).
     */
    @Query("""
            select t from Transaction t
            where t.userId = :userId
              and t.occurredAt < :toExclusive
              and (cast(:from as Timestamp) is null or t.occurredAt >= :from)
              and not exists (select r.id from Retrospect r where r.transactionId = t.id)
            order by t.occurredAt desc, t.id desc
            """)
    List<Transaction> findCandidates(@Param("userId") long userId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("toExclusive") OffsetDateTime toExclusive,
                                     Pageable pageable);

    /** 이상치 기준선 — 최근 N일 거래 전부 (E-62 ④). 그룹화는 서비스가 한다. */
    List<Transaction> findAllByUserIdAndOccurredAtGreaterThanEqual(long userId, OffsetDateTime from);

    /** 묶음 명명 표본 — 이 묶음에 배정된 거래 (E-64). */
    List<Transaction> findAllByBehaviorIdOrderByOccurredAtDesc(Long behaviorId);
}
