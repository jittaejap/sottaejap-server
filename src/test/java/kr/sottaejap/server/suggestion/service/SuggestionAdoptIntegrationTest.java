package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.service.GoalService;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.service.RetrospectWriter;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionView;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 회고 저장 → 재계산 → 제안 자동 생성 → 채택 → 목표 전망까지 실제 PostgreSQL에서 돌린다 (E-81~E-83).
 *
 * <p>이 슬라이스의 핵심 주장은 "제안을 만드는 API가 없다"이다 — 회고를 저장하기만 하면 제안이 생겨야 하고,
 * 재계산이 다시 돌아도 채택한 금액이 흔들리지 않아야 한다. 테스트 트랜잭션은 롤백된다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class SuggestionAdoptIntegrationTest {

    private long userId;

    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SuggestionService suggestionService;
    @Autowired
    private GoalService goalService;

    @BeforeEach
    void createIsolatedUser() {
        User user = User.social(AuthProvider.KAKAO, "suggestion-it-" + System.nanoTime(), "통합테스트", null);
        ReflectionTestUtils.setField(user, "monthlyBudget", 1_000_000);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 회고_셋을_저장하면_제안이_저절로_생긴다() {
        writeThreeLowRetrospects();

        List<SuggestionView> suggestions = suggestionService.list(userId, null).suggestions();

        assertEquals(1, suggestions.size());
        SuggestionView suggestion = suggestions.getFirst();
        assertEquals(SuggestionStatus.PROPOSED, suggestion.status());
        // 기본 횟수는 그 달 거래 건수, 절감액은 평균 단가 × 횟수
        assertEquals(3, suggestion.adjustCount());
        assertEquals(12_000, suggestion.avgAmount());
        assertEquals(36_000, suggestion.expectedSaving());
        assertNotNull(suggestion.reason());
    }

    @Test
    void 채택하면_목표_전망이_움직이고_재계산해도_금액이_흔들리지_않는다() {
        writeThreeLowRetrospects();
        GoalView goal = goalService.create(userId, new GoalRequest("여행 자금", 1_000_000, 0));
        long suggestionId = suggestionService.list(userId, null).suggestions().getFirst().id();

        SuggestionView adopted = suggestionService.adopt(userId, suggestionId,
                new SuggestionAdoptRequest(2, goal.id()));

        assertEquals(SuggestionStatus.ADOPTED, adopted.status());
        assertEquals(24_000, adopted.expectedSaving());

        GoalView afterAdopt = goalService.list(userId).goals().getFirst();
        assertEquals(24_000, afterAdopt.adoptedSaving());
        assertEquals(0.0, afterAdopt.achievementRate());
        assertEquals(0.024, afterAdopt.projectedRate(), 1e-9);

        // 회고를 하나 더 저장하면 재계산이 돈다 — 채택한 제안은 그대로여야 한다
        retrospectWriter.write(userId, request(save("2026-08-23T23:40:00+09:00", 30_000, "it-4")));

        SuggestionView afterRecompute = suggestionService.list(userId, null).suggestions().getFirst();
        assertEquals(suggestionId, afterRecompute.id());
        assertEquals(SuggestionStatus.ADOPTED, afterRecompute.status());
        assertEquals(24_000, afterRecompute.expectedSaving());
        assertEquals(1, suggestionService.list(userId, null).suggestions().size());
    }

    @Test
    void 거절한_제안은_기본_목록에서_빠지고_재계산해도_되살아나지_않는다() {
        writeThreeLowRetrospects();
        long suggestionId = suggestionService.list(userId, null).suggestions().getFirst().id();

        suggestionService.reject(userId, suggestionId);

        assertTrue(suggestionService.list(userId, null).suggestions().isEmpty());
        assertEquals(1, suggestionService.list(userId, SuggestionStatus.REJECTED).suggestions().size());

        retrospectWriter.write(userId, request(save("2026-08-23T23:40:00+09:00", 30_000, "it-4")));

        assertTrue(suggestionService.list(userId, null).suggestions().isEmpty());
    }

    @Test
    void 내부_AI_목록은_외부_기본_목록과_같다() {
        writeThreeLowRetrospects();

        assertEquals(suggestionService.list(userId, null).suggestions(),
                suggestionService.internalList(userId).suggestions());
    }

    /** 같은 키(배달|NIGHT|충동|혼자) LOW 회고 3건 — 보류를 넘겨 RESOLVED · ADJUST가 된다. */
    private void writeThreeLowRetrospects() {
        retrospectWriter.write(userId, request(save("2026-08-20T23:10:00+09:00", 12_000, "it-1")));
        retrospectWriter.write(userId, request(save("2026-08-21T23:20:00+09:00", 15_000, "it-2")));
        retrospectWriter.write(userId, request(save("2026-08-22T23:30:00+09:00", 9_000, "it-3")));
    }

    private Transaction save(String occurredAt, int amount, String hash) {
        return transactionRepository.saveAndFlush(Transaction.of(userId, OffsetDateTime.parse(occurredAt),
                "테스트배달", amount, "배달", "배달", hash + System.nanoTime()));
    }

    private static RetrospectSaveRequest request(Transaction transaction) {
        return new RetrospectSaveRequest(transaction.getId(), Satisfaction.LOW, "충동", "혼자", false, null);
    }
}
