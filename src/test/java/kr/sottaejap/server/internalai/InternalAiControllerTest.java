package kr.sottaejap.server.internalai;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.dto.MapPointView;
import kr.sottaejap.server.analysis.service.AnalysisService;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.retrospect.dto.ClusterMemoryView;
import kr.sottaejap.server.retrospect.dto.MemoryResponse;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import kr.sottaejap.server.retrospect.dto.ReflectionView;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.service.RetrospectService;
import kr.sottaejap.server.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI SpringClient가 보는 내부 API 모양 (05 §3) — snake_case 본문 · camelCase 응답 · data는 object. */
@ExtendWith(MockitoExtension.class)
class InternalAiControllerTest {

    @Mock
    private TransactionService transactionService;
    @Mock
    private RetrospectService retrospectService;
    @Mock
    private AnalysisService analysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InternalAiController(transactionService, retrospectService, analysisService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void save_reflection은_snake_case_본문을_받아_저장하고_object를_돌려준다() throws Exception {
        when(retrospectService.save(eq(1L), any())).thenReturn(new RetrospectSaveResponse(12L, "심야 배달", 3,
                -0.25, 36000, 12000, 3, 0.036, EvaluationStatus.RESOLVED, null, Verdict.ADJUST));

        mockMvc.perform(post("/internal/ai/users/1/reflections").contentType(MediaType.APPLICATION_JSON).content("""
                        {"transaction_id":1043,"purpose":"충동","companion":"혼자","satisfaction":"LOW","repeat_intention":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.behaviorId").value(12))
                .andExpect(jsonPath("$.data.verdict").value("ADJUST"));

        ArgumentCaptor<RetrospectSaveRequest> captor = ArgumentCaptor.forClass(RetrospectSaveRequest.class);
        verify(retrospectService).save(eq(1L), captor.capture());
        assertEquals(1043L, captor.getValue().transactionId());
        assertEquals("혼자", captor.getValue().companion());
        assertEquals(Boolean.FALSE, captor.getValue().repeatIntent());
        assertNull(captor.getValue().source());
        assertEquals(RetrospectSource.CANDIDATE, captor.getValue().sourceOrDefault());
    }

    @Test
    void save_reflection_중복은_409_봉투다() throws Exception {
        when(retrospectService.save(eq(1L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT));

        mockMvc.perform(post("/internal/ai/users/1/reflections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_id\":1043,\"satisfaction\":\"LOW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RETROSPECT"));
    }

    @Test
    void save_reflection에_transaction_id가_없으면_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(post("/internal/ai/users/1/reflections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"satisfaction\":\"LOW\",\"purpose\":\"충동\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(retrospectService);
    }

    @Test
    void get_reflections는_문서_표_모양이다() throws Exception {
        when(retrospectService.findReflections(1L)).thenReturn(List.of(
                new ReflectionView(5L, 1043L, Satisfaction.LOW, "충동", "혼자", false, RetrospectStatus.COMPLETED)));

        mockMvc.perform(get("/internal/ai/users/1/reflections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reflections[0].id").value(5))
                .andExpect(jsonPath("$.data.reflections[0].transactionId").value(1043))
                .andExpect(jsonPath("$.data.reflections[0].repeatIntent").value(false))
                .andExpect(jsonPath("$.data.reflections[0].status").value("COMPLETED"));
    }

    @Test
    void get_memory는_clusters와_recentReflections를_돌려준다() throws Exception {
        when(retrospectService.memory(1L)).thenReturn(new MemoryResponse(
                List.of(new ClusterMemoryView(12L, "심야 배달", "배달|NIGHT|충동|혼자", 3, -0.25, Verdict.ADJUST)),
                List.of(new ReflectionView(5L, 1043L, Satisfaction.LOW, "충동", "혼자", null, RetrospectStatus.COMPLETED))));

        mockMvc.perform(get("/internal/ai/users/1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusters[0].behaviorId").value(12))
                .andExpect(jsonPath("$.data.clusters[0].clusterKey").value("배달|NIGHT|충동|혼자"))
                .andExpect(jsonPath("$.data.clusters[0].verdict").value("ADJUST"))
                .andExpect(jsonPath("$.data.recentReflections[0].id").value(5));
    }
    @Test
    void get_behavior_analysis는_집계와_points를_주고_highlight는_주지_않는다() throws Exception {
        when(analysisService.internalAnalysis(1L)).thenReturn(new InternalAnalysisResponse(
                "2026-08",
                List.of(new VerdictSummary(Verdict.SUSTAIN, 0, 0, 0.0),
                        new VerdictSummary(Verdict.ADJUST, 1, 36_000, 0.036)),
                new PendingSummary(0, 0, 0.0),
                List.of(new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 36_000, Verdict.ADJUST)),
                List.of(new MapPointView(12L, "심야 배달", 36_000, 12_000, 3, 0.036, -0.25, 3,
                        EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST, "처방", null))));

        mockMvc.perform(get("/internal/ai/users/1/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byVerdict[1].verdict").value("ADJUST"))
                .andExpect(jsonPath("$.data.byVerdict[1].share").value(0.036))
                .andExpect(jsonPath("$.data.byCategory[0].category").value("배달"))
                .andExpect(jsonPath("$.data.points[0].behaviorId").value(12))
                .andExpect(jsonPath("$.data.highlight").doesNotExist());
    }
}
