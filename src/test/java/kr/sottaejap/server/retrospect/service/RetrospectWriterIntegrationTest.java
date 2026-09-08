package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 저장 → 전체 재계산을 실제 PostgreSQL에서 끝까지 돌린다 (E-59 · E-61). AI 명명은 부르지 않는다(RetrospectServiceImpl 몫).
 * 같은 영속성 컨텍스트에서 재계산이 배정한 behaviorId가 writer에 보이는지가 핵심이다. 테스트 트랜잭션은 롤백된다.
 * 데모 계정에는 실측 데이터가 남아 있을 수 있어 사용자를 새로 만들어 격리한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class RetrospectWriterIntegrationTest {

    private long userId;

    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private BehaviorClusterRepository behaviorClusterRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void createIsolatedUser() {
        User user = User.social(AuthProvider.KAKAO, "it-" + System.nanoTime(), "통합테스트", null);
        ReflectionTestUtils.setField(user, "monthlyBudget", 1_000_000);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 회고_셋을_저장하면_리프가_RESOLVED가_되고_behaviorId와_평균이_채워진다() {
        Transaction first = save("2026-08-20T23:10:00+09:00", 12000, "it-1");
        Transaction second = save("2026-08-21T23:20:00+09:00", 15000, "it-2");
        Transaction third = save("2026-08-22T23:30:00+09:00", 9000, "it-3");

        Long leafAfterFirst = retrospectWriter.write(userId, request(first, Satisfaction.LOW));
        BehaviorCluster pending = behaviorClusterRepository.findById(leafAfterFirst).orElseThrow();
        assertEquals(EvaluationStatus.PENDING, pending.getEvaluationStatus());
        assertNull(pending.getVerdict());
        assertNotNull(pending.getParentId());
        assertEquals(leafAfterFirst, first.getBehaviorId());

        retrospectWriter.write(userId, request(second, Satisfaction.LOW));
        Long leafAfterThird = retrospectWriter.write(userId, request(third, Satisfaction.HIGH));

        BehaviorCluster leaf = behaviorClusterRepository.findById(leafAfterThird).orElseThrow();
        assertEquals(leafAfterFirst, leafAfterThird);
        assertEquals("배달|NIGHT|충동|혼자", leaf.getClusterKey());
        assertEquals(3, leaf.getRetrospectCount());
        assertEquals(EvaluationStatus.RESOLVED, leaf.getEvaluationStatus());
        assertEquals(Verdict.ADJUST, leaf.getVerdict());
        assertNull(leaf.getParentId());
        assertEquals("2026-08", leaf.getAnalysisYearMonth());
        assertEquals(36000, leaf.getMonthlyTotalAmount());
        assertEquals(0.036, leaf.getBurdenRatio(), 1e-9);
        assertEquals(-1.0 / 3, userRepository.findById(userId).orElseThrow().getAvgSatisfaction(), 1e-9);
        assertEquals(List.of(leafAfterThird, leafAfterThird, leafAfterThird),
                List.of(first.getBehaviorId(), second.getBehaviorId(), third.getBehaviorId()));
    }

    @Test
    void 같은_거래를_다시_저장하면_409다() {
        Transaction transaction = save("2026-08-20T23:10:00+09:00", 12000, "it-dup");
        retrospectWriter.write(userId, request(transaction, Satisfaction.LOW));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> retrospectWriter.write(userId, request(transaction, Satisfaction.HIGH)));
        assertEquals(CommonErrorCode.DUPLICATE_RETROSPECT, exception.getErrorCode());
    }

    private Transaction save(String occurredAt, int amount, String hash) {
        return transactionRepository.saveAndFlush(Transaction.of(userId, OffsetDateTime.parse(occurredAt),
                "테스트배달", amount, "배달", "배달", hash));
    }

    private static RetrospectSaveRequest request(Transaction transaction, Satisfaction satisfaction) {
        return new RetrospectSaveRequest(transaction.getId(), satisfaction, "충동", "혼자", false, null);
    }
}
