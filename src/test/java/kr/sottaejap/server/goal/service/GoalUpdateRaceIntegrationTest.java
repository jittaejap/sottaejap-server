package kr.sottaejap.server.goal.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code PUT /goals/{id}}와 월간 리포트 확정의 실적 배분이 같은 행에 겹치는 순서를 실제 PostgreSQL로 재현한다 (E-100 · server #36).
 *
 * <p>수정 트랜잭션이 목표를 먼저 읽고(실적 0), 그 사이에 확정 트랜잭션이 {@link GoalRepository#addCurrentAmount}로
 * 20,000을 더해 커밋하고, 수정 트랜잭션이 이름만 바꿔 커밋한다. 전체 행을 쓰면 읽어 둔 0이 20,000을 덮는다.
 * 두 트랜잭션이 정말 따로 커밋돼야 하므로 테스트 트랜잭션으로 감싸지 않고 만든 행은 직접 지운다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class GoalUpdateRaceIntegrationTest {

    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long userId;
    private long goalId;

    @BeforeEach
    void createGoalWithoutProgress() {
        User user = User.social(AuthProvider.KAKAO, "goal-race-" + System.nanoTime(), "통합테스트", null);
        userId = userRepository.save(user).getId();
        goalId = goalRepository.save(Goal.create(userId, "여행 자금", 1_000_000, LocalDate.of(2026, 12, 25), 0)).getId();
    }

    @AfterEach
    void deleteCreatedRows() {
        goalRepository.deleteById(goalId);
        userRepository.deleteById(userId);
    }

    @Test
    void 실적을_생략한_수정은_그_사이_확정이_더한_실적을_덮지_않고_예정일도_유지한다() {
        TransactionTemplate update = new TransactionTemplate(transactionManager);
        TransactionTemplate finalizeLastMonth = new TransactionTemplate(transactionManager);
        finalizeLastMonth.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        update.executeWithoutResult(status -> {
            Goal alreadyRead = goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).orElseThrow();
            finalizeLastMonth.executeWithoutResult(inner -> goalRepository.addCurrentAmount(goalId, 20_000));
            alreadyRead.update("새 이름", 2_000_000, null, null);
        });

        Goal stored = goalRepository.findById(goalId).orElseThrow();
        assertEquals("새 이름", stored.getName());
        assertEquals(2_000_000, stored.getTargetAmount());
        assertEquals(20_000, stored.getCurrentAmount());
        // target_date 왕복 — V10 컬럼과 LocalDate 매핑, 그리고 예정일도 생략하면 유지된다
        assertEquals(LocalDate.of(2026, 12, 25), stored.getTargetDate());
    }
}
