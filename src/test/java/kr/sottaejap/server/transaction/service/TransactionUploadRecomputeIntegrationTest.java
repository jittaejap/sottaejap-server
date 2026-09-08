package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.ai.AiClient;
import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.service.RetrospectWriter;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 새 달 CSV를 올리면 기준월과 금액이 함께 움직인다 (E-95 · 06 R24 · 이슈 #27).
 *
 * <p>이 이슈가 걸리는 흐름은 <b>매달 새 CSV를 올리는 순서</b>(S2)다 — 회고를 먼저 하고 그 다음 달 거래를 올린다.
 * 온보딩(업로드 → 표본 회고)은 순서가 반대라 이 창을 밟지 않는다. 고치기 전에는 업로드가 재계산을 부르지 않아
 * 기준월만 8월로 바뀌고(E-78) 묶음 행에는 7월 금액 36,000원이 남았다(E-61).
 *
 * <p>AI는 {@code @MockitoBean}으로 세워 두고 <b>한 번도 부르지 않는지</b> 확인한다. 업로드 뒤에는
 * {@code byCategory}가 비어 AI를 부르지 않는 것이 계약이다 (E-91).
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class TransactionUploadRecomputeIntegrationTest {

    private static final int MONTHLY_BUDGET = 1_000_000;
    private static final String AUGUST_CSV = """
            거래일자,가맹점명,거래금액
            2026-08-25 20:22,○○마트,12000
            """;

    private long userId;

    @Autowired
    private TransactionUploadFacade uploadFacade;
    @Autowired
    private RetrospectWriter retrospectWriter;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AnalysisService analysisService;

    @MockitoBean
    private AiClient aiClient;

    @BeforeEach
    void createIsolatedUser() {
        User user = User.social(AuthProvider.KAKAO, "upload-it-" + System.nanoTime(), "통합테스트", null);
        ReflectionTestUtils.setField(user, "monthlyBudget", MONTHLY_BUDGET);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void 새_달_CSV를_올리면_기준월과_묶음_금액이_함께_바뀐다() {
        writeThreeJulyRetrospects();

        // 업로드 전 — 기준월은 7월이고 묶음 행에 7월 합계가 들어 있다.
        InternalAnalysisResponse before = analysisService.internalAnalysis(userId);
        assertEquals("2026-07", before.analysisYearMonth());
        assertEquals(36_000, before.points().getFirst().monthlyTotalAmount());

        TransactionUploadResponse uploaded = uploadFacade.upload(userId,
                new MockMultipartFile("file", "aug.csv", "text/csv", AUGUST_CSV.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, uploaded.importedCount());

        // 업로드 뒤 — 기준월이 8월로 가고 묶음 행의 8월 합계는 0이다. 재계산이 없으면 여기서 36,000이 남는다.
        InternalAnalysisResponse after = analysisService.internalAnalysis(userId);
        assertEquals("2026-08", after.analysisYearMonth());
        assertEquals(1, after.points().size());
        assertEquals(0, after.points().getFirst().monthlyTotalAmount());

        // 기준월 합계가 0이면 카테고리는 통째로 빈다 (E-73) — 그래서 AI를 부르지 않는다 (E-91).
        AnalysisResponse analysis = analysisService.analysis(userId);
        assertEquals("2026-08", analysis.analysisYearMonth());
        assertEquals(List.of(), analysis.byCategory());
        verify(aiClient, never()).chat(any());
    }

    /** 같은 키(배달|NIGHT|충동|혼자) 회고 3건 — 보류 임계값 3을 넘겨 RESOLVED · ADJUST가 되고 합계는 36,000원이다. */
    private void writeThreeJulyRetrospects() {
        retrospectWriter.write(userId, request(save("2026-07-20T23:10:00+09:00", 12_000, "upload-it-jul-1")));
        retrospectWriter.write(userId, request(save("2026-07-21T23:20:00+09:00", 15_000, "upload-it-jul-2")));
        retrospectWriter.write(userId, request(save("2026-07-22T23:30:00+09:00", 9_000, "upload-it-jul-3")));
    }

    private Transaction save(String occurredAt, int amount, String hash) {
        return transactionRepository.saveAndFlush(Transaction.of(userId, OffsetDateTime.parse(occurredAt),
                "테스트배달", amount, "배달", "배달", hash + System.nanoTime()));
    }

    private static RetrospectSaveRequest request(Transaction transaction) {
        return new RetrospectSaveRequest(transaction.getId(), Satisfaction.LOW, "충동", "혼자", false, null);
    }
}
