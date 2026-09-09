package kr.sottaejap.server.suggestion.controller;

import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionListResponse;
import kr.sottaejap.server.suggestion.dto.SuggestionView;
import kr.sottaejap.server.suggestion.service.SuggestionService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /suggestions HTTP 계약 (05 §2 #15 · #16). */
@ExtendWith(MockitoExtension.class)
class SuggestionControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private SuggestionService suggestionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SuggestionController(suggestionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 목록은_묶음_수치와_이유를_같이_준다() throws Exception {
        when(suggestionService.list(eq(USER_ID), isNull())).thenReturn(new SuggestionListResponse(List.of(view())));

        mockMvc.perform(get("/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestions[0].id").value(7))
                .andExpect(jsonPath("$.data.suggestions[0].behaviorName").value("심야 배달"))
                .andExpect(jsonPath("$.data.suggestions[0].expectedSaving").value(96_000))
                .andExpect(jsonPath("$.data.suggestions[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.data.suggestions[0].reason").exists());
    }

    @Test
    void status_쿼리를_그대로_넘긴다() throws Exception {
        when(suggestionService.list(USER_ID, SuggestionStatus.REJECTED))
                .thenReturn(new SuggestionListResponse(List.of()));

        mockMvc.perform(get("/suggestions").param("status", "REJECTED"))
                .andExpect(status().isOk());

        verify(suggestionService).list(USER_ID, SuggestionStatus.REJECTED);
    }

    @Test
    void status_오타는_400이다() throws Exception {
        mockMvc.perform(get("/suggestions").param("status", "ADOPTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    void 채택_본문을_그대로_넘긴다() throws Exception {
        when(suggestionService.adopt(eq(USER_ID), eq(7L), any())).thenReturn(view());

        mockMvc.perform(post("/suggestions/7/adopt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustCount\":2,\"goalId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));

        ArgumentCaptor<SuggestionAdoptRequest> captor = ArgumentCaptor.forClass(SuggestionAdoptRequest.class);
        verify(suggestionService).adopt(eq(USER_ID), eq(7L), captor.capture());
        assertEquals(2, captor.getValue().adjustCount());
        assertEquals(3L, captor.getValue().goalId());
    }

    @Test
    void 조정_횟수가_없거나_0이면_400이다() throws Exception {
        mockMvc.perform(post("/suggestions/7/adopt").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/suggestions/7/adopt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustCount\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(suggestionService);
    }

    @Test
    void 거절은_본문이_없다() throws Exception {
        when(suggestionService.reject(USER_ID, 7L)).thenReturn(view());

        mockMvc.perform(post("/suggestions/7/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 거절한_제안을_다시_채택하면_400이다() throws Exception {
        when(suggestionService.adopt(eq(USER_ID), eq(7L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        mockMvc.perform(post("/suggestions/7/adopt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private static SuggestionView view() {
        return new SuggestionView(7L, 12L, "심야 배달", 96_000, 12_000, 8, -0.42, Quadrant.PRIORITY,
                8, 96_000, null, SuggestionStatus.PROPOSED, "심야 배달의 이번 달 지출이 96,000원이에요. …");
    }
}
