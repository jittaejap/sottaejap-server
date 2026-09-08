package kr.sottaejap.server.transaction.repository;

import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 후보 쿼리(E-62 ①②) — 회고된 거래 제외 · 컷오프 · from null 허용 · 최신순. 실제 PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class TransactionRepositoryCandidateTest {

    private static final long DEMO_USER_ID = 1L;
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2026-08-20T12:00:00+09:00");
    private static final OffsetDateTime RECENT = OffsetDateTime.parse("2026-08-22T23:10:00+09:00");
    private static final OffsetDateTime TODAY = OffsetDateTime.parse("2026-08-24T09:00:00+09:00");
    private static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-08-24T00:00:00+09:00");

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private RetrospectRepository retrospectRepository;

    private Transaction old;
    private Transaction recent;

    @BeforeEach
    void saveThree() {
        old = transactionRepository.save(Transaction.of(DEMO_USER_ID, OLD, "cand-old", 5000, "기타", null, "cand-old"));
        recent = transactionRepository.save(Transaction.of(DEMO_USER_ID, RECENT, "cand-recent", 12000, "기타", null, "cand-recent"));
        transactionRepository.save(Transaction.of(DEMO_USER_ID, TODAY, "cand-today", 3000, "기타", null, "cand-today"));
        transactionRepository.flush();
    }

    @Test
    void 컷오프_이전만_최신순으로_나오고_from이_없어도_된다() {
        List<Transaction> candidates = transactionRepository.findCandidates(DEMO_USER_ID, null, CUTOFF, PageRequest.of(0, 100));

        List<String> merchants = candidates.stream().map(Transaction::getMerchant)
                .filter(m -> m.startsWith("cand-")).toList();
        assertEquals(List.of("cand-recent", "cand-old"), merchants);
    }

    @Test
    void 이미_회고된_거래는_빠진다() {
        retrospectRepository.saveAndFlush(Retrospect.completed(recent.getId(), Satisfaction.LOW, null, null, null,
                RetrospectSource.CANDIDATE, TODAY));

        List<String> merchants = transactionRepository.findCandidates(DEMO_USER_ID, null, CUTOFF, PageRequest.of(0, 100))
                .stream().map(Transaction::getMerchant).toList();

        assertFalse(merchants.contains("cand-recent"));
        assertTrue(merchants.contains("cand-old"));
    }

    @Test
    void from을_주면_그_이후만_본다() {
        List<String> merchants = transactionRepository.findCandidates(DEMO_USER_ID, RECENT, CUTOFF, PageRequest.of(0, 100))
                .stream().map(Transaction::getMerchant).filter(m -> m.startsWith("cand-")).toList();

        assertEquals(List.of("cand-recent"), merchants);
    }

    @Test
    void 최근_거래로_기준월을_구한다() {
        Transaction latest = transactionRepository.findTopByUserIdOrderByOccurredAtDesc(DEMO_USER_ID).orElseThrow();
        assertTrue(!latest.getOccurredAt().isBefore(TODAY.minusSeconds(1)));
        assertEquals(old.getId(), transactionRepository.findByIdAndUserId(old.getId(), DEMO_USER_ID).orElseThrow().getId());
        assertTrue(transactionRepository.findByIdAndUserId(old.getId(), 999L).isEmpty());
    }
}
