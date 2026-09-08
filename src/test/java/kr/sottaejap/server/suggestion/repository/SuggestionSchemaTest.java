package kr.sottaejap.server.suggestion.repository;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V7의 부분 유일 인덱스를 실제 PostgreSQL로 확인한다 (E-81).
 *
 * <p>여기서 지키는 불변식은 "한 묶음에 열린 제안은 하나"다. 코드로만 지키면 재계산이 겹쳐 도는 순간
 * 두 줄이 생기고, 그건 목록에서야 눈에 띈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class SuggestionSchemaTest {

    private static final long DEMO_USER_ID = 1L;

    @Autowired
    private SuggestionRepository suggestionRepository;
    @Autowired
    private BehaviorClusterRepository behaviorClusterRepository;

    @Test
    void 제안을_저장하고_사용자로_조회한다() {
        Long behaviorId = cluster();

        suggestionRepository.saveAndFlush(Suggestion.propose(behaviorId, 8, 96_000));

        List<Suggestion> found = suggestionRepository.findAllByUserId(DEMO_USER_ID).stream()
                .filter(suggestion -> suggestion.getBehaviorId().equals(behaviorId))
                .toList();
        assertEquals(1, found.size());
        assertEquals(96_000, found.getFirst().getExpectedSaving());
        assertEquals(SuggestionStatus.PROPOSED, found.getFirst().getStatus());
    }

    @Test
    void 같은_묶음에_열린_제안은_하나뿐이다() {
        Long behaviorId = cluster();
        suggestionRepository.saveAndFlush(Suggestion.propose(behaviorId, 8, 96_000));

        assertThrows(DataIntegrityViolationException.class,
                () -> suggestionRepository.saveAndFlush(Suggestion.propose(behaviorId, 4, 48_000)));
    }

    @Test
    void 채택한_제안이_있어도_새_제안을_넣을_수_있다() {
        Long behaviorId = cluster();
        Suggestion adopted = Suggestion.propose(behaviorId, 8, 96_000);
        adopted.adopt(2, 24_000, null);
        suggestionRepository.saveAndFlush(adopted);

        // 부분 유일 인덱스는 PROPOSED만 본다 — 전체 유일이면 채택 이력이 새 제안을 막는다
        suggestionRepository.saveAndFlush(Suggestion.propose(behaviorId, 4, 48_000));

        assertEquals(2, suggestionRepository.findAllByBehaviorIdIn(List.of(behaviorId)).size());
    }

    @Test
    void 없는_묶음을_가리키는_제안은_저장되지_않는다() {
        assertThrows(DataIntegrityViolationException.class,
                () -> suggestionRepository.saveAndFlush(Suggestion.propose(9_999_999L, 1, 100)));
    }

    /** 실측 데이터와 부딪히지 않도록 매번 새 묶음을 만든다. */
    private Long cluster() {
        String key = "테스트|NIGHT|충동|" + System.nanoTime();
        BehaviorCluster cluster = BehaviorCluster.create(DEMO_USER_ID, key);
        cluster.apply(new ClusterEvaluation(key, null, 4, -0.5, -0.42, 12_000, 96_000,
                YearMonth.of(2026, 8), 8, 0.096, EvaluationStatus.RESOLVED, Quadrant.MINOR,
                Verdict.ADJUST, List.of(), List.of()), null);
        return behaviorClusterRepository.saveAndFlush(cluster).getId();
    }
}
