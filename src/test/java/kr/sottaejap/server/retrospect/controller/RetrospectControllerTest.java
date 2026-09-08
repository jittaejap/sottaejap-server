package kr.sottaejap.server.retrospect.controller;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.ReflectionStep;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.dto.ReflectionDraft;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.service.RetrospectService;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /retrospects/* HTTP 계약 (05 §2) — 본문·쿼리 매핑, 봉투, 오류 코드. 서비스는 목이다.
 * standaloneSetup에는 Security가 없으므로 @AuthenticationPrincipal은 테스트용 리졸버가 사용자 1로 채운다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private RetrospectService retrospectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RetrospectController(retrospectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 후보_목록은_봉투와_KST_오프셋으로_나온다() throws Exception {
        CandidateView view = new CandidateView(1043L, OffsetDateTime.parse("2026-08-22T23:10:00+09:00"),
                "○○배달", 12000, "배달", TimeSlot.NIGHT, ReasonCode.TIMESLOT_OUTLIER, "이유");
        when(retrospectService.candidates(eq(USER_ID), any(), any(), any()))
                .thenReturn(new CandidateListResponse(List.of(view)));

        mockMvc.perform(get("/retrospects/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidates[0].transactionId").value(1043))
                .andExpect(jsonPath("$.data.candidates[0].occurredAt").value("2026-08-22T23:10:00+09:00"))
                .andExpect(jsonPath("$.data.candidates[0].reasonCode").value("TIMESLOT_OUTLIER"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(retrospectService).candidates(USER_ID, null, null, null);
    }

    @Test
    void 후보_쿼리_limit_from_to가_그대로_전달된다() throws Exception {
        when(retrospectService.candidates(anyLong(), anyInt(), any(), any()))
                .thenReturn(new CandidateListResponse(List.of()));

        mockMvc.perform(get("/retrospects/candidates").param("limit", "20")
                        .param("from", "2026-08-22").param("to", "2026-08-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates").isEmpty());

        verify(retrospectService).candidates(USER_ID, 20, LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 24));
    }

    @Test
    void 잘못된_날짜는_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(get("/retrospects/candidates").param("from", "어제"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        verifyNoInteractions(retrospectService);
    }

    @Test
    void 회고_저장은_본문을_매핑하고_리프_묶음을_돌려준다() throws Exception {
        when(retrospectService.save(eq(USER_ID), any())).thenReturn(new RetrospectSaveResponse(12L, "심야 배달", 4,
                -0.42, 96000, 12000, 8, 0.08, EvaluationStatus.RESOLVED, Quadrant.PRIORITY, Verdict.ADJUST));

        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON).content("""
                        {"transactionId":1043,"satisfaction":"LOW","purpose":"충동","companion":"혼자","repeatIntent":false,"source":"CANDIDATE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.behaviorId").value(12))
                .andExpect(jsonPath("$.data.behaviorName").value("심야 배달"))
                .andExpect(jsonPath("$.data.verdict").value("ADJUST"));

        ArgumentCaptor<RetrospectSaveRequest> captor = ArgumentCaptor.forClass(RetrospectSaveRequest.class);
        verify(retrospectService).save(eq(USER_ID), captor.capture());
        assertEquals(1043L, captor.getValue().transactionId());
        assertEquals(Satisfaction.LOW, captor.getValue().satisfaction());
        assertEquals("충동", captor.getValue().purpose());
        assertEquals(Boolean.FALSE, captor.getValue().repeatIntent());
    }

    @Test
    void PENDING_응답은_quadrant와_verdict가_null이다() throws Exception {
        when(retrospectService.save(eq(USER_ID), any())).thenReturn(new RetrospectSaveResponse(12L, null, 1,
                0.0, 12000, 12000, 1, null, EvaluationStatus.PENDING, null, null));

        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1043,\"satisfaction\":\"UNKNOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.quadrant").doesNotExist())
                .andExpect(jsonPath("$.data.verdict").doesNotExist());
    }

    @Test
    void 중복_회고는_409_DUPLICATE_RETROSPECT다() throws Exception {
        when(retrospectService.save(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT));

        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1043,\"satisfaction\":\"LOW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RETROSPECT"));
    }

    @Test
    void 표준_태그_밖은_400_INVALID_TAG다() throws Exception {
        when(retrospectService.save(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_TAG));

        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1043,\"satisfaction\":\"LOW\",\"purpose\":\"야식\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TAG"));
    }

    @Test
    void transactionId나_satisfaction이_없으면_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"satisfaction\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/retrospects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1043,\"satisfaction\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        verifyNoInteractions(retrospectService);
    }

    @Test
    void 대화_턴은_본문을_매핑하고_다음_단계를_돌려준다() throws Exception {
        when(retrospectService.chat(eq(USER_ID), any())).thenReturn(new RetrospectChatResponse("만족하셨나요?",
                ReflectionStep.SATISFACTION, new ReflectionDraft(Satisfaction.UNKNOWN, null, null, null),
                true, List.of("satisfaction"), false));

        mockMvc.perform(post("/retrospects/chat").contentType(MediaType.APPLICATION_JSON).content("""
                        {"transactionId":1043,"message":"안녕","step":"INTRO",
                         "reflection":{"satisfaction":"UNKNOWN","purpose":null,"companion":null,"repeatIntent":null},
                         "recentMessages":[{"role":"assistant","content":"이전"}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("만족하셨나요?"))
                .andExpect(jsonPath("$.data.step").value("SATISFACTION"))
                .andExpect(jsonPath("$.data.uncertainFields[0]").value("satisfaction"))
                .andExpect(jsonPath("$.data.fallback").value(false));

        ArgumentCaptor<RetrospectChatRequest> captor = ArgumentCaptor.forClass(RetrospectChatRequest.class);
        verify(retrospectService).chat(eq(USER_ID), captor.capture());
        assertEquals(ReflectionStep.INTRO, captor.getValue().step());
        assertEquals("이전", captor.getValue().recentMessages().get(0).content());
        assertNull(captor.getValue().reflection().purpose());
    }

    @Test
    void AI가_없으면_503_LLM_UNAVAILABLE이다() throws Exception {
        when(retrospectService.chat(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.LLM_UNAVAILABLE));

        mockMvc.perform(post("/retrospects/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1043}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("LLM_UNAVAILABLE"));
    }

    @Test
    void 남의_거래는_404_NOT_FOUND다() throws Exception {
        when(retrospectService.chat(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(post("/retrospects/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

}
