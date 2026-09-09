package kr.sottaejap.server.user.controller;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import kr.sottaejap.server.user.dto.UserMeResponse;
import kr.sottaejap.server.user.dto.UserSettingsRequest;
import kr.sottaejap.server.user.service.UserService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT /users/me/settings HTTP 계약 (05 §1 API 3). */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 설정을_바꾸면_갱신된_내_정보를_돌려준다() throws Exception {
        when(userService.updateSettings(eq(USER_ID), any())).thenReturn(new UserMeResponse(
                1L, "demo@sottaejap.kr", "데모 사용자", AuthProvider.LOCAL,
                2_500_000, 2.0, 150_000, 1, true, "2026-08"));

        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyBudget\":2500000,\"outlierThreshold\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.monthlyBudget").value(2_500_000))
                .andExpect(jsonPath("$.data.outlierBaseAmount").value(150_000));

        ArgumentCaptor<UserSettingsRequest> captor = ArgumentCaptor.forClass(UserSettingsRequest.class);
        verify(userService).updateSettings(eq(USER_ID), captor.capture());
        assertEquals(2_500_000, captor.getValue().monthlyBudget());
        assertNull(captor.getValue().retrospectDelayDays());
    }

    @Test
    void 기준_금액만_보내도_서비스까지_실린다() throws Exception {
        when(userService.updateSettings(eq(USER_ID), any())).thenReturn(new UserMeResponse(
                1L, "demo@sottaejap.kr", "데모 사용자", AuthProvider.LOCAL,
                2_500_000, 2.0, 150_000, 1, true, "2026-08"));

        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlierBaseAmount\":150000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outlierBaseAmount").value(150_000));

        ArgumentCaptor<UserSettingsRequest> captor = ArgumentCaptor.forClass(UserSettingsRequest.class);
        verify(userService).updateSettings(eq(USER_ID), captor.capture());
        assertEquals(150_000, captor.getValue().outlierBaseAmount());
        assertNull(captor.getValue().monthlyBudget());
    }

    @Test
    void 기준_금액이_0_이하면_400이다() throws Exception {
        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlierBaseAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(userService);
    }

    @Test
    void 예산이_0_이하면_400이다() throws Exception {
        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyBudget\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(userService);
    }

    @Test
    void 바꿀_값이_하나도_없으면_400이다() throws Exception {
        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(userService);
    }

    @Test
    void D_N이_상한을_넘으면_400이다() throws Exception {
        mockMvc.perform(put("/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retrospectDelayDays\":31}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
