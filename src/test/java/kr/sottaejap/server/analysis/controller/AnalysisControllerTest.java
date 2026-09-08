package kr.sottaejap.server.analysis.controller;

import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.CtaView;
import kr.sottaejap.server.analysis.dto.MapPointView;
import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.common.enums.CtaType;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /analysis · /satisfaction-map HTTP 계약 (05 §2 API 22 · 14). 서비스는 목이다. */
@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private AnalysisService analysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalysisController(analysisService), new SatisfactionMapController(analysisService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 분석은_집계와_한_문장을_봉투에_담아_준다() throws Exception {
        when(analysisService.analysis(USER_ID)).thenReturn(new AnalysisResponse(
                "2026-08",
                List.of(new VerdictSummary(Verdict.SUSTAIN, 1, 168_000, 0.168),
                        new VerdictSummary(Verdict.ADJUST, 1, 96_000, 0.096)),
                new PendingSummary(1, 24_000, 0.024),
                List.of(new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 96_000, Verdict.ADJUST)),
                "배달에 이번 달 96,000원을 썼어요."));

        mockMvc.perform(get("/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisYearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.byVerdict[1].verdict").value("ADJUST"))
                .andExpect(jsonPath("$.data.byVerdict[1].share").value(0.096))
                .andExpect(jsonPath("$.data.pending.clusterCount").value(1))
                .andExpect(jsonPath("$.data.byCategory[0].dominantTimeSlot").value("NIGHT"))
                .andExpect(jsonPath("$.data.highlight").exists());
    }

    @Test
    void 없는_사용자는_404다() throws Exception {
        when(analysisService.analysis(USER_ID)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(get("/analysis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 지도는_축_경계와_CTA를_같이_준다() throws Exception {
        when(analysisService.satisfactionMap(USER_ID)).thenReturn(new SatisfactionMapResponse(
                "2026-08",
                new SatisfactionMapResponse.AxisXView("지출 부담", "MONTHLY_TOTAL_OVER_BUDGET", 1_000_000),
                new SatisfactionMapResponse.AxisYView("보정 만족도", List.of(-1, 1)),
                new SatisfactionMapResponse.BoundariesView(0.1, 0.0),
                List.of(point(1L, Quadrant.MINOR, Verdict.ADJUST, new CtaView(CtaType.ADJUST, "조정하기")),
                        point(2L, null, null, null))));

        mockMvc.perform(get("/satisfaction-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boundaries.x").value(0.1))
                .andExpect(jsonPath("$.data.boundaries.y").value(0.0))
                .andExpect(jsonPath("$.data.axisX.monthlyBudget").value(1_000_000))
                .andExpect(jsonPath("$.data.points[0].cta.type").value("ADJUST"))
                .andExpect(jsonPath("$.data.points[0].cta.label").value("조정하기"))
                .andExpect(content().string(containsString("\"quadrant\":null")))
                // 05 §2는 보류 묶음에도 `"cta": null`을 싣는다 — 키가 사라지면 화면이 필드 유무로 분기하게 된다
                .andExpect(content().string(containsString("\"cta\":null")));
    }

    private static MapPointView point(long behaviorId, Quadrant quadrant, Verdict verdict, CtaView cta) {
        EvaluationStatus status = quadrant == null ? EvaluationStatus.PENDING : EvaluationStatus.RESOLVED;
        return new MapPointView(behaviorId, "심야 배달", 96_000, 12_000, 8, 0.096, -0.42, 4,
                status, quadrant, verdict, "처방", cta);
    }
}
