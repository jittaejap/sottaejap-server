package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V1 retrospects 스키마와 엔티티 매핑을 실제 PostgreSQL로 확인한다 (ddl validate · UNIQUE · 조인 조회).
 * 데모 계정(users.id = 1)을 쓰고 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class RetrospectSchemaTest {

    private static final long DEMO_USER_ID = 1L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-07T10:00:00+09:00");

    @Autowired
    private RetrospectRepository retrospectRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Transaction delivery;

    @BeforeEach
    void saveTransaction() {
        delivery = transactionRepository.save(Transaction.of(DEMO_USER_ID,
                OffsetDateTime.parse("2026-08-22T23:10:00+09:00"), "테스트배달", 12000, "배달", "배달", "schema-test-delivery"));
        transactionRepository.flush();
    }

    @Test
    void 회고를_저장하고_거래_조인으로_사용자별_조회한다() {
        retrospectRepository.save(Retrospect.completed(delivery.getId(), Satisfaction.LOW, "충동", "혼자", false,
                RetrospectSource.CANDIDATE, NOW));
        retrospectRepository.flush();

        List<RetrospectWithTransaction> rows = retrospectRepository.findAllWithTransactionByUserId(DEMO_USER_ID);

        RetrospectWithTransaction row = rows.stream()
                .filter(r -> r.transaction().getId().equals(delivery.getId())).findFirst().orElseThrow();
        assertEquals(RetrospectStatus.COMPLETED, row.retrospect().getStatus());
        assertEquals("충동", row.retrospect().getPurpose());
        assertEquals("테스트배달", row.transaction().getMerchant());
    }

    @Test
    void 같은_거래에_두_번_저장하면_유일_제약이_막는다() {
        retrospectRepository.save(Retrospect.completed(delivery.getId(), Satisfaction.LOW, null, null, null,
                RetrospectSource.CANDIDATE, NOW));
        retrospectRepository.flush();

        // IDENTITY 전략이라 save 시점에 insert가 나가고 거기서 UNIQUE가 걸린다 — 서비스도 이 예외를 409로 바꾼다
        assertThrows(DataIntegrityViolationException.class, () -> retrospectRepository.saveAndFlush(
                Retrospect.completed(delivery.getId(), Satisfaction.HIGH, null, null, null, RetrospectSource.MANUAL, NOW)));
    }
}
