package kr.sottaejap.server.transaction.repository;

import kr.sottaejap.server.transaction.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

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
}
