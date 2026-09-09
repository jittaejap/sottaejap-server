package kr.sottaejap.server.transaction.repository;

import kr.sottaejap.server.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * 중복 판별용 해시를 한 번에 읽는다. 행마다 exists를 부르면 1,000건 업로드가 1,000질의가 된다 (NFR-03).
     */
    @Query("select t.importHash from Transaction t where t.userId = :userId")
    List<String> findImportHashesByUserId(@Param("userId") long userId);

    /**
     * 외부 목록(05 §2 `GET /transactions` · E-93)과 내부 AI 조회(05 §3)가 같이 쓰는 질의. 정렬은 양쪽 모두
     * {@code occurredAt desc, id desc}로 고정이다 — 같은 시각의 거래가 페이지 경계에서 흔들리지 않는다.
     *
     * <p>선택 조건은 cast로 타입을 먼저 알려 준다. 맨 파라미터로 `:from is null`을 쓰면 PostgreSQL이
     * "could not determine data type of parameter"로 거절한다.
     *
     * <p>{@code hasRetrospect}는 null이면 전체, true면 회고 있는 거래만(회고 이력 탭), false면 없는 거래만이다.
     * 내부 AI 조회는 null을 넘긴다.
     */
    @Query("""
            select t from Transaction t
            where t.userId = :userId
              and (cast(:from as Timestamp) is null or t.occurredAt >= :from)
              and (cast(:to as Timestamp) is null or t.occurredAt < :to)
              and (cast(:category as String) is null or t.category = :category)
              and (cast(:hasRetrospect as Boolean) is null
                   or (:hasRetrospect = true
                       and exists (select r.id from Retrospect r where r.transactionId = t.id))
                   or (:hasRetrospect = false
                       and not exists (select r.id from Retrospect r where r.transactionId = t.id)))
            order by t.occurredAt desc, t.id desc
            """)
    Page<Transaction> search(@Param("userId") long userId,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to,
                             @Param("category") String category,
                             @Param("hasRetrospect") Boolean hasRetrospect,
                             Pageable pageable);

    Optional<Transaction> findByIdAndUserId(Long id, long userId);

    /** analysisYearMonth = 최근 거래월 (E-60). */
    Optional<Transaction> findTopByUserIdOrderByOccurredAtDesc(long userId);

    /** 월간 리포트의 하한 — 첫 거래월 이전 달은 스냅샷을 만들지 않는다 (E-94). */
    Optional<Transaction> findTopByUserIdOrderByOccurredAtAsc(long userId);

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

    /** 월간 리포트 — 기간 안의 거래 전부 (E-94). 회고 여부와 무관하게 읽는다 — totalSpending은 전체 지출이다. */
    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.occurredAt >= :from and t.occurredAt < :toExclusive
            order by t.occurredAt asc, t.id asc
            """)
    List<Transaction> findAllInRange(@Param("userId") long userId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("toExclusive") OffsetDateTime toExclusive);

    /** 묶음 명명 표본 — 이 묶음에 배정된 거래 (E-64). */
    List<Transaction> findAllByBehaviorIdOrderByOccurredAtDesc(Long behaviorId);

    /** 묶음 상세 — 상위 묶음과 그 자식 리프의 거래를 한 번에 읽는다 (FR-07-05 · E-59). */
    List<Transaction> findAllByBehaviorIdInOrderByOccurredAtDescIdDesc(Collection<Long> behaviorIds);
}
