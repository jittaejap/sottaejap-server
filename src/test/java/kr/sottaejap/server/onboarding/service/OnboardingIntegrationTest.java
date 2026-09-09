package kr.sottaejap.server.onboarding.service;

import kr.sottaejap.server.analysis.service.BehaviorService;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.service.RetrospectWriter;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 온보딩 3·4단계를 실제 PostgreSQL에서 끝까지 돌린다 (E-92 · FR-09-01,02).
 *
 * <p>단위 테스트는 전부 목이라 두 가지를 증명하지 못한다. 첫째, `start`가 거는 페이지 조건이 실제 JPQL에서
 * 도는지. 둘째, `complete`의 `recomputeAll` 직후 읽는 `findEffectiveByUserId`가 같은 트랜잭션에서 flush를 타서
 * `clusterCount`가 정말 `GET /behaviors`의 개수와 같은지. 여기서 그 둘을 확인한다.
 *
 * <p>데모 계정에는 실측 데이터가 남아 있을 수 있어 사용자를 새로 만들어 격리한다. 테스트 트랜잭션은 롤백된다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class OnboardingIntegrationTest {

    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate PERIOD_TO = LocalDate.of(2026, 7, 31);

    private long userId;

    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private BehaviorService behaviorService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void createIsolatedUser() {
        User user = User.social(AuthProvider.KAKAO, "onboarding-it-" + System.nanoTime(), "온보딩통합", null);
        ReflectionTestUtils.setField(user, "monthlyBudget", 1_000_000);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 표본은_기간의_처음과_끝을_모두_덮는다() {
        // 6/1부터 하루 간격 9건. 3건을 고르면 최신순 0·3·6번째 — 앞에서 3건을 자르면 마지막 사흘만 남는다.
        List<Transaction> saved = saveDaily(9);

        CandidateListResponse response = onboardingService.start(userId, new OnboardingStartRequest(3,
                PERIOD_FROM, PERIOD_TO));

        assertEquals(3, response.candidates().size());
        assertTrue(response.candidates().stream()
                .allMatch(candidate -> candidate.reasonCode() == ReasonCode.ONBOARDING_SAMPLE));
        assertEquals(List.of(saved.get(8).getId(), saved.get(5).getId(), saved.get(2).getId()),
                response.candidates().stream().map(candidate -> candidate.transactionId()).toList());
    }

    @Test
    void 완료의_clusterCount는_지도의_점_개수와_같다() {
        List<Transaction> saved = saveDaily(3);
        retrospectWriter.write(userId, retrospect(saved.get(0), Satisfaction.LOW));
        retrospectWriter.write(userId, retrospect(saved.get(1), Satisfaction.HIGH));

        OnboardingCompleteResponse response = onboardingService.complete(userId);

        assertTrue(response.onboardingCompleted());
        assertTrue(userRepository.findById(userId).orElseThrow().isOnboardingCompleted());
        assertEquals(behaviorService.behaviors(userId).behaviors().size(), response.clusterCount());
    }

    @Test
    void 회고가_없어도_완료는_저장된다() {
        OnboardingCompleteResponse response = onboardingService.complete(userId);

        assertEquals(0, response.clusterCount());
        assertTrue(userRepository.findById(userId).orElseThrow().isOnboardingCompleted());
    }

    /** 6/1부터 하루 간격. 반환 순서는 오래된 것부터다 — 후보 목록은 반대(최신순)로 온다. */
    private List<Transaction> saveDaily(int count) {
        List<Transaction> saved = new ArrayList<>();
        for (int day = 0; day < count; day++) {
            saved.add(transactionRepository.saveAndFlush(Transaction.of(userId,
                    PERIOD_FROM.plusDays(day).atTime(23, 10).atZone(TimeSlot.ZONE).toOffsetDateTime(),
                    "테스트배달", 12_000 + day, "배달", "배달", "onboarding-it-" + userId + "-" + day)));
        }
        return saved;
    }

    private static RetrospectSaveRequest retrospect(Transaction transaction, Satisfaction satisfaction) {
        return new RetrospectSaveRequest(transaction.getId(), satisfaction, "충동", "혼자", false, null);
    }
}
