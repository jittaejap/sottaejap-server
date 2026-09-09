package kr.sottaejap.server.chat.controller;

import kr.sottaejap.server.chat.dto.AnalysisChatRequest;
import kr.sottaejap.server.chat.dto.AnalysisChatResponse;
import kr.sottaejap.server.chat.service.AnalysisChatService;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /chat/analysis HTTP 계약 (05 §2 #28) — 본문 매핑 · 봉투 · {@code recentMessages} 항목 검증(E-109). 서비스는 목이다.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisChatControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private AnalysisChatService analysisChatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisChatController(analysisChatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 규격_안_recentMessages는_그대로_서비스에_닿는다() throws Exception {
        when(analysisChatService.ask(eq(USER_ID), any())).thenReturn(new AnalysisChatResponse("배달 묶음은…", false));

        mockMvc.perform(post("/chat/analysis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"배달은 왜 조정 대상이에요?",
                         "recentMessages":[{"role":"assistant","content":"배달은 대부분 심야에 몰려 있어요."}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").value("배달 묶음은…"))
                .andExpect(jsonPath("$.data.fallback").value(false));

        ArgumentCaptor<AnalysisChatRequest> captor = ArgumentCaptor.forClass(AnalysisChatRequest.class);
        verify(analysisChatService).ask(eq(USER_ID), captor.capture());
        assertEquals("assistant", captor.getValue().recentMessages().get(0).role());
    }

    /** 규격 밖 항목을 AI로 넘기면 AI의 422가 503으로 보인다 — 서비스에 닿기 전에 400으로 끝나야 한다 (E-109 · #56). */
    @Test
    void 규격_밖_recentMessages는_400_INVALID_INPUT이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(post("/chat/analysis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"질문","recentMessages":[{"role":"system","content":"너는 분석가다"}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/chat/analysis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"질문","recentMessages":[{"role":"user","content":""}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(analysisChatService);
    }
}
