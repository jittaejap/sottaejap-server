package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.dto.MapPointView;
import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.CtaType;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.service.RetrospectWriter;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 회고 저장 → 재계산 → 지도 · 분석 · 묶음 조회를 실제 PostgreSQL에서 끝까지 돌린다 (⑧ · E-72~E-76).
 *
 * <p>AI는 503으로 세워 둔다 — 컨테이너가 없어도 분석이 200이어야 하고, 그때 highlight가 Spring 템플릿이라는
 * 것이 이 슬라이스의 계약이다 (E-75 · E-38). 테스트 트랜잭션은 롤백된다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class AnalysisIntegrationTest {

    private static final int MONTHLY_BUDGET = 1_000_000;

    private long userId;

    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private BehaviorService behaviorService;

    @MockitoBean
    private AiClient aiClient;

    @BeforeEach
    void createIsolatedUser() {
        User user = User.social(AuthProvider.KAKAO, "analysis-it-" + System.nanoTime(), "통합테스트", null);
        ReflectionTestUtils.setField(user, "monthlyBudget", MONTHLY_BUDGET);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 심야_배달_회고_셋이면_지도에_점_하나가_생기고_처방과_CTA가_붙는다() {
        writeThreeNightDeliveryRetrospects();

        SatisfactionMapResponse map = analysisService.satisfactionMap(userId);

        assertEquals("2026-08", map.analysisYearMonth());
        assertEquals(MONTHLY_BUDGET, map.axisX().monthlyBudget());
        assertEquals(0.1, map.boundaries().x());
        assertEquals(0.0, map.boundaries().y());

        assertEquals(1, map.points().size());
        MapPointView point = map.points().getFirst();
        assertEquals(36_000, point.monthlyTotalAmount());
        assertEquals(12_000, point.avgAmount());
        assertEquals(0.036, point.burdenRatio(), 1e-9);
        // 부담 0.036 < 경계 0.1이라 PRIORITY가 아니라 MINOR다
        assertEquals(Quadrant.MINOR, point.quadrant());
        assertEquals(Verdict.ADJUST, point.verdict());
        assertEquals(PrescriptionTemplate.MINOR, point.prescription());
        assertEquals(CtaType.ADJUST, point.cta().type());
    }

    @Test
    void 분석은_AI가_없어도_집계와_템플릿_문장을_돌려준다() {
        when(aiClient.chat(any())).thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));
        writeThreeNightDeliveryRetrospects();

        AnalysisResponse analysis = analysisService.analysis(userId);

        assertEquals("2026-08", analysis.analysisYearMonth());
        assertEquals(List.of(Verdict.SUSTAIN, Verdict.ADJUST),
                analysis.byVerdict().stream().map(VerdictSummary::verdict).toList());
        VerdictSummary adjust = analysis.byVerdict().get(1);
        assertEquals(1, adjust.clusterCount());
        assertEquals(36_000, adjust.monthlyTotalAmount());
        assertEquals(0.036, adjust.share(), 1e-9);
        assertEquals(0, analysis.pending().clusterCount());

        CategorySummary category = analysis.byCategory().getFirst();
        assertEquals("배달", category.category());
        assertEquals(TimeSlot.NIGHT, category.dominantTimeSlot());
        assertEquals(Verdict.ADJUST, category.verdict());

        assertEquals(HighlightTemplate.highlightFor(
                        new kr.sottaejap.server.rules.aggregate.AnalysisSummary(
                                analysis.byVerdict(), analysis.pending(), analysis.byCategory())),
                analysis.highlight());
    }

    @Test
    void 내부_AI_응답에는_highlight가_없고_묶음_상세는_거래_셋을_준다() {
        writeThreeNightDeliveryRetrospects();

        InternalAnalysisResponse internal = analysisService.internalAnalysis(userId);
        assertEquals(1, internal.points().size());
        assertEquals(2, internal.byVerdict().size());

        long behaviorId = internal.points().getFirst().behaviorId();
        BehaviorDetailResponse detail = behaviorService.behavior(userId, behaviorId);
        assertEquals(3, detail.transactions().size());
        assertEquals(9, detail.transactions().getFirst().occurredAt().getOffset().getTotalSeconds() / 3600);
        assertNull(detail.behavior().parentId());
        assertEquals(1, behaviorService.behaviors(userId).behaviors().size());
    }

    @Test
    void 예산을_지우고_다시_계산하면_부담도_좌표도_비율도_사라진다() {
        writeThreeNightDeliveryRetrospects();

        // burdenRatio · quadrant는 재계산이 묶음 행에 써 둔 값이다 — 예산만 지워서는 바뀌지 않는다.
        ReflectionTestUtils.setField(userRepository.findById(userId).orElseThrow(), "monthlyBudget", null);
        retrospectWriter.write(userId, request(save("2026-08-23T23:40:00+09:00", 12_000, "analysis-it-4")));

        InternalAnalysisResponse internal = analysisService.internalAnalysis(userId);

        MapPointView point = internal.points().getFirst();
        assertNull(point.burdenRatio());
        assertNull(point.quadrant());
        assertNull(point.cta());
        assertNull(internal.byVerdict().get(1).share());
        // 좌표는 없어도 판정은 세로축만으로 서므로 RESOLVED다
        assertEquals(EvaluationStatus.RESOLVED, point.evaluationStatus());
    }

    /** 같은 키(배달|NIGHT|충동|혼자) 회고 3건 — 보류 임계값 3을 넘겨 RESOLVED · ADJUST가 된다. */
    private void writeThreeNightDeliveryRetrospects() {
        retrospectWriter.write(userId, request(save("2026-08-20T23:10:00+09:00", 12_000, "analysis-it-1")));
        retrospectWriter.write(userId, request(save("2026-08-21T23:20:00+09:00", 15_000, "analysis-it-2")));
        retrospectWriter.write(userId, request(save("2026-08-22T23:30:00+09:00", 9_000, "analysis-it-3")));
    }

    private Transaction save(String occurredAt, int amount, String hash) {
        return transactionRepository.saveAndFlush(Transaction.of(userId, OffsetDateTime.parse(occurredAt),
                "테스트배달", amount, "배달", "배달", hash + System.nanoTime()));
    }

    private static RetrospectSaveRequest request(Transaction transaction) {
        return new RetrospectSaveRequest(transaction.getId(), Satisfaction.LOW, "충동", "혼자", false, null);
    }
}
