package kr.sottaejap.server.onboarding.controller;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.onboarding.service.OnboardingService;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.CandidateView;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /onboarding HTTP 계약 (05 §2 #18 · #21). */
@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    private static final long USER_ID = 1L;
    private static final String START_BODY =
            "{\"sampleSize\":20,\"periodFrom\":\"2026-06-01\",\"periodTo\":\"2026-07-31\"}";

    @Mock
    private OnboardingService onboardingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(onboardingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 표본은_후보와_같은_모양으로_내려간다() throws Exception {
        when(onboardingService.start(eq(USER_ID), any())).thenReturn(new CandidateListResponse(List.of(
                new CandidateView(1043L, OffsetDateTime.parse("2026-07-22T23:10+09:00"), "○○배달", 12_000,
                        "배달", TimeSlot.NIGHT, ReasonCode.ONBOARDING_SAMPLE,
                        "최근 소비 중에서 함께 돌아볼 거래로 골랐어요."))));

        mockMvc.perform(post("/onboarding/start").contentType(MediaType.APPLICATION_JSON).content(START_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidates[0].transactionId").value(1043))
                .andExpect(jsonPath("$.data.candidates[0].reasonCode").value("ONBOARDING_SAMPLE"))
                .andExpect(jsonPath("$.data.candidates[0].occurredAt").value("2026-07-22T23:10:00+09:00"));

        ArgumentCaptor<OnboardingStartRequest> captor = ArgumentCaptor.forClass(OnboardingStartRequest.class);
        verify(onboardingService).start(eq(USER_ID), captor.capture());
        assertEquals(20, captor.getValue().sampleSize());
        assertEquals(LocalDate.of(2026, 6, 1), captor.getValue().periodFrom());
        assertEquals(LocalDate.of(2026, 7, 31), captor.getValue().periodTo());
    }

    @Test
    void 표본_수가_없거나_1보다_작거나_기간이_비면_400이다() throws Exception {
        mockMvc.perform(post("/onboarding/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodFrom\":\"2026-06-01\",\"periodTo\":\"2026-07-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/onboarding/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleSize\":0,\"periodFrom\":\"2026-06-01\",\"periodTo\":\"2026-07-31\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/onboarding/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleSize\":20,\"periodTo\":\"2026-07-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(onboardingService);
    }

    @Test
    void 시작일이_종료일보다_뒤면_400이다() throws Exception {
        when(onboardingService.start(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        mockMvc.perform(post("/onboarding/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleSize\":20,\"periodFrom\":\"2026-07-31\",\"periodTo\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 완료는_본문_없이_부르고_묶음_수를_돌려준다() throws Exception {
        when(onboardingService.complete(USER_ID)).thenReturn(new OnboardingCompleteResponse(true, 15));

        mockMvc.perform(post("/onboarding/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.clusterCount").value(15));
    }
}
