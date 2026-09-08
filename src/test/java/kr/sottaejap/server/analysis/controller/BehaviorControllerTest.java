package kr.sottaejap.server.analysis.controller;

import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.BehaviorListResponse;
import kr.sottaejap.server.analysis.dto.BehaviorView;
import kr.sottaejap.server.analysis.service.BehaviorService;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import kr.sottaejap.server.transaction.dto.TransactionAiView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /behaviors HTTP 계약 (05 §2 · FR-07-05). */
@ExtendWith(MockitoExtension.class)
class BehaviorControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private BehaviorService behaviorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BehaviorController(behaviorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 목록은_봉투에_담긴_배열이다() throws Exception {
        when(behaviorService.behaviors(USER_ID)).thenReturn(new BehaviorListResponse(List.of(view())));

        mockMvc.perform(get("/behaviors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.behaviors[0].behaviorId").value(1))
                .andExpect(jsonPath("$.data.behaviors[0].name").value("심야 배달"));
    }

    @Test
    void 상세의_거래_시각은_한국_오프셋으로_나간다() throws Exception {
        TransactionAiView transaction = new TransactionAiView(11L,
                OffsetDateTime.parse("2026-08-24T22:30:00+09:00"), "배달의민족", 12_000, "배달", TimeSlot.NIGHT, 1L);
        when(behaviorService.behavior(USER_ID, 1L))
                .thenReturn(new BehaviorDetailResponse(view(), List.of(transaction)));

        mockMvc.perform(get("/behaviors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].occurredAt").value("2026-08-24T22:30:00+09:00"))
                .andExpect(jsonPath("$.data.behavior.clusterKey").value("배달|NIGHT||"));
    }

    @Test
    void 없는_묶음은_404다() throws Exception {
        when(behaviorService.behavior(USER_ID, 999L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(get("/behaviors/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private static BehaviorView view() {
        return new BehaviorView(1L, "심야 배달", "배달|NIGHT||", null, 96_000, 12_000, 8, 4, 0.096, -0.42,
                EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST);
    }
}
