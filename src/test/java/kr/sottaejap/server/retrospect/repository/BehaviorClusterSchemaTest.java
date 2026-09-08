package kr.sottaejap.server.retrospect.repository;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V1 behavior_clusters의 CHECK · UNIQUE · CHAR(7) 매핑을 실제 PostgreSQL로 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class BehaviorClusterSchemaTest {

    private static final long DEMO_USER_ID = 1L;

    @Autowired
    private BehaviorClusterRepository repository;

    @Test
    void 산출값을_저장하고_기준월은_char7로_왕복한다() {
        BehaviorCluster cluster = BehaviorCluster.create(DEMO_USER_ID, "배달|NIGHT|충동|혼자");
        cluster.apply(evaluation(EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST), null);
        repository.saveAndFlush(cluster);

        BehaviorCluster saved = repository.findByUserIdAndClusterKey(DEMO_USER_ID, "배달|NIGHT|충동|혼자").orElseThrow();
        assertEquals("2026-08", saved.getAnalysisYearMonth());
        assertEquals(Verdict.ADJUST, saved.getVerdict());
        assertEquals(0.036, saved.getBurdenRatio(), 1e-9);
        assertNull(saved.getDisplayName());
    }

    @Test
    void PENDING인데_verdict가_있으면_CHECK가_막는다() {
        BehaviorCluster cluster = BehaviorCluster.create(DEMO_USER_ID, "배달|NIGHT|충동|혼자");
        cluster.apply(evaluation(EvaluationStatus.PENDING, null, Verdict.ADJUST), null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(cluster));
    }

    @Test
    void 같은_사용자_같은_키는_유일하다() {
        BehaviorCluster first = BehaviorCluster.create(DEMO_USER_ID, "배달|NIGHT||");
        first.apply(evaluation(EvaluationStatus.PENDING, null, null), null);
        repository.saveAndFlush(first);

        BehaviorCluster second = BehaviorCluster.create(DEMO_USER_ID, "배달|NIGHT||");
        second.apply(evaluation(EvaluationStatus.PENDING, null, null), null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(second));
    }

    private static ClusterEvaluation evaluation(EvaluationStatus status, Quadrant quadrant, Verdict verdict) {
        return new ClusterEvaluation("배달|NIGHT|충동|혼자", null, 3, -1.0 / 3, -0.25, 12000, 36000,
                YearMonth.of(2026, 8), 3, 0.036, status, quadrant, verdict, List.of(1L, 2L, 3L), List.of("테스트배달"));
    }
}
