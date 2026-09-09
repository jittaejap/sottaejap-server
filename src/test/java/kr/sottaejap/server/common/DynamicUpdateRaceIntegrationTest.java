package kr.sottaejap.server.common;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서로 다른 요청이 겹치지 않는 컬럼을 각자 읽어 고치는 엔티티 셋 — {@code User} · {@code Suggestion} · {@code BehaviorCluster} —
 * 을 실제 PostgreSQL에서 같은 순서로 겹쳐 본다 (E-100 보강 · server PR #42 리뷰). {@code Goal}은
 * {@code GoalUpdateRaceIntegrationTest}가 맡는다.
 *
 * <p>순서는 하나다 — 바깥 트랜잭션이 행을 읽고, 그 사이 다른 트랜잭션({@code REQUIRES_NEW})이 다른 컬럼을 고쳐 커밋하고,
 * 바깥이 자기 컬럼만 고쳐 커밋한다. 전체 행을 쓰면 바깥이 읽어 둔 옛 값이 사이의 커밋을 덮는다.
 * 두 트랜잭션이 정말 따로 커밋돼야 하므로 테스트 트랜잭션으로 감싸지 않고 만든 행은 직접 지운다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class DynamicUpdateRaceIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BehaviorClusterRepository behaviorClusterRepository;
    @Autowired
    private SuggestionRepository suggestionRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate outer;
    private TransactionTemplate committedInBetween;

    private long userId;
    private long clusterId;
    private long suggestionId;
    private long goalId;

    @BeforeEach
    void createRowsAndTemplates() {
        outer = new TransactionTemplate(transactionManager);
        committedInBetween = new TransactionTemplate(transactionManager);
        committedInBetween.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        User user = User.social(AuthProvider.KAKAO, "dynamic-update-race-" + System.nanoTime(), "통합테스트", null);
        userId = userRepository.save(user).getId();
        clusterId = behaviorClusterRepository.save(BehaviorCluster.create(userId, "식비|야식|충동|혼자")).getId();
        suggestionId = suggestionRepository.save(Suggestion.propose(clusterId, 3, 30_000)).getId();
        goalId = goalRepository.save(Goal.create(userId, "여행 자금", 1_000_000, null, 0)).getId();
    }

    @AfterEach
    void deleteCreatedRows() {
        suggestionRepository.deleteById(suggestionId);
        goalRepository.deleteById(goalId);
        behaviorClusterRepository.deleteById(clusterId);
        userRepository.deleteById(userId);
    }

    /** 예산을 바꾸지 않는 설정 변경은 재계산 없이 커밋한다 — 그 사이 다른 요청의 재계산이 낸 평균이 남아야 한다. */
    @Test
    void 설정_변경은_그_사이_재계산이_낸_평균_만족도를_덮지_않는다() {
        outer.executeWithoutResult(status -> {
            User alreadyRead = userRepository.findById(userId).orElseThrow();
            committedInBetween.executeWithoutResult(inner ->
                    userRepository.findById(userId).orElseThrow().updateAvgSatisfaction(0.75));
            alreadyRead.updateSettings(null, 2.5, null);
        });

        User stored = userRepository.findById(userId).orElseThrow();
        assertEquals(0.75, stored.getAvgSatisfaction());
        assertEquals(2.5, stored.getOutlierThreshold());
    }

    /** 재계산의 refresh는 수치만 고친다 — 그 사이 사용자가 커밋한 채택과 목표 연결이 남아야 한다. */
    @Test
    void 재계산의_refresh는_그_사이_커밋된_채택을_되돌리지_않는다() {
        outer.executeWithoutResult(status -> {
            Suggestion alreadyRead = suggestionRepository.findById(suggestionId).orElseThrow();
            committedInBetween.executeWithoutResult(inner ->
                    suggestionRepository.findById(suggestionId).orElseThrow().adopt(2, 20_000, goalId));
            alreadyRead.refresh(4, 40_000);
        });

        Suggestion stored = suggestionRepository.findById(suggestionId).orElseThrow();
        assertEquals(SuggestionStatus.ADOPTED, stored.getStatus());
        assertEquals(goalId, stored.getGoalId());
        assertEquals(4, stored.getAdjustCount());
    }

    /** 재계산의 markEmpty는 지표만 비운다 — 그 사이 명명이 커밋한 이름이 남아야 한다. */
    @Test
    void 재계산은_그_사이_명명이_지은_묶음_이름을_지우지_않는다() {
        outer.executeWithoutResult(status -> {
            BehaviorCluster alreadyRead = behaviorClusterRepository.findById(clusterId).orElseThrow();
            committedInBetween.executeWithoutResult(inner ->
                    behaviorClusterRepository.findById(clusterId).orElseThrow().rename("심야 배달"));
            alreadyRead.markEmpty();
        });

        BehaviorCluster stored = behaviorClusterRepository.findById(clusterId).orElseThrow();
        assertEquals("심야 배달", stored.getDisplayName());
        assertTrue(stored.isEmpty());
        assertNull(stored.getParentId());
    }
}
