package kr.sottaejap.server.transaction.repository;

import kr.sottaejap.server.common.enums.CardIssuer;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.dto.TransactionAiView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI가 부르는 조회(05 §3)를 실제 PostgreSQL에 대고 확인한다.
 *
 * <p>선택 조건을 비운 호출이 핵심이다. JPQL에서 파라미터를 맨 채로 {@code is null} 비교하면
 * PostgreSQL이 "could not determine data type of parameter"로 거절한다 — H2에서는 통과하므로
 * 반드시 실제 DB로 돌려야 잡힌다. 데모 계정(V1의 users.id = 1)을 그대로 쓰고 트랜잭션은 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class TransactionRepositorySearchTest {

    private static final long DEMO_USER_ID = 1L;
    private static final OffsetDateTime NIGHT_DELIVERY = OffsetDateTime.parse("2026-08-22T23:10:00+09:00");
    private static final OffsetDateTime MORNING_CAFE = OffsetDateTime.parse("2026-08-23T08:15:00+09:00");

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void saveTwoTransactions() {
        transactionRepository.save(Transaction.of(DEMO_USER_ID, NIGHT_DELIVERY, "테스트배달", 12000,
                "배달", "배달", CardIssuer.KB, "test-hash-delivery"));
        transactionRepository.save(Transaction.of(DEMO_USER_ID, MORNING_CAFE, "테스트카페", 4500,
                "카페", "카페", CardIssuer.KB, "test-hash-cafe"));
        transactionRepository.flush();
    }

    @Test
    void 선택_조건이_모두_비어도_조회된다() {
        List<String> merchants = merchantsOf(transactionRepository.search(
                DEMO_USER_ID, null, null, null, PageRequest.of(0, 100)));

        assertTrue(merchants.contains("테스트배달"));
        assertTrue(merchants.contains("테스트카페"));
    }

    @Test
    void 기간과_카테고리로_좁힐_수_있다() {
        List<Transaction> onlyDelivery = transactionRepository.search(
                DEMO_USER_ID, NIGHT_DELIVERY, MORNING_CAFE, "배달", PageRequest.of(0, 100));

        assertEquals(List.of("테스트배달"), merchantsOf(onlyDelivery));
    }

    @Test
    void 최신순으로_돌려준다() {
        List<Transaction> found = transactionRepository.search(
                DEMO_USER_ID, NIGHT_DELIVERY, null, null, PageRequest.of(0, 100));

        assertEquals(List.of("테스트카페", "테스트배달"), merchantsOf(found));
    }

    @Test
    void AI에_내보내는_시각은_한국_오프셋이다() {
        Transaction saved = transactionRepository
                .search(DEMO_USER_ID, NIGHT_DELIVERY, MORNING_CAFE, "배달", PageRequest.of(0, 100))
                .stream()
                .filter(transaction -> transaction.getMerchant().equals("테스트배달"))
                .findFirst()
                .orElseThrow();

        // DB에서 읽으면 Hibernate가 UTC로 정규화하므로 DTO가 되돌린다 (05 §0).
        assertEquals("2026-08-22T23:10+09:00", TransactionAiView.from(saved).occurredAt().toString());
    }

    @Test
    void 중복_판별용_해시를_한_번에_읽는다() {
        assertTrue(transactionRepository.findImportHashesByUserId(DEMO_USER_ID).contains("test-hash-cafe"));
    }

    private static List<String> merchantsOf(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getMerchant)
                .filter(merchant -> merchant.startsWith("테스트"))
                .toList();
    }
}
