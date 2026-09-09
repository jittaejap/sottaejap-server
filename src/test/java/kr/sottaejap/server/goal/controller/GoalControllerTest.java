package kr.sottaejap.server.goal.controller;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.goal.dto.GoalListResponse;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.service.GoalService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /goals HTTP 계약 (05 §2 #4 · #5 · #5a). */
@ExtendWith(MockitoExtension.class)
class GoalControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private GoalService goalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GoalController(goalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 목록은_달성률과_전망을_같이_준다() throws Exception {
        when(goalService.list(USER_ID)).thenReturn(new GoalListResponse(
                List.of(new GoalView(3L, "여행 자금", 1_000_000, LocalDate.of(2026, 12, 25), 250_000, 24_000, 0.25, 0.274))));

        mockMvc.perform(get("/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.goals[0].adoptedSaving").value(24_000))
                .andExpect(jsonPath("$.data.goals[0].achievementRate").value(0.25))
                .andExpect(jsonPath("$.data.goals[0].projectedRate").value(0.274))
                .andExpect(jsonPath("$.data.goals[0].targetDate").value("2026-12-25"));
    }

    @Test
    void 등록_본문을_그대로_넘긴다() throws Exception {
        when(goalService.create(eq(USER_ID), any()))
                .thenReturn(new GoalView(3L, "여행 자금", 1_000_000, null, 0, 0, 0.0, 0.0));

        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행 자금\",\"targetAmount\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));

        ArgumentCaptor<GoalRequest> captor = ArgumentCaptor.forClass(GoalRequest.class);
        verify(goalService).create(eq(USER_ID), captor.capture());
        assertEquals("여행 자금", captor.getValue().name());
        assertNull(captor.getValue().currentAmount());
        // 지금 배포된 온보딩은 예정일을 보내지 않는다 — 그래도 200이어야 한다 (선택 항목)
        assertNull(captor.getValue().targetDate());
    }

    @Test
    void 달성_예정일을_실으면_그대로_넘어간다() throws Exception {
        when(goalService.create(eq(USER_ID), any()))
                .thenReturn(new GoalView(3L, "여행 자금", 1_000_000, LocalDate.of(2026, 12, 25), 0, 0, 0.0, 0.0));

        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행 자금\",\"targetAmount\":1000000,\"targetDate\":\"2026-12-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetDate").value("2026-12-25"));

        ArgumentCaptor<GoalRequest> captor = ArgumentCaptor.forClass(GoalRequest.class);
        verify(goalService).create(eq(USER_ID), captor.capture());
        assertEquals(LocalDate.of(2026, 12, 25), captor.getValue().targetDate());
    }

    @Test
    void 달성_예정일_형식이_틀리면_400이다() throws Exception {
        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\",\"targetAmount\":1000000,\"targetDate\":\"2026-13-99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\",\"targetAmount\":1000000,\"targetDate\":\"2026/12/25\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 형식이 틀리면 본문을 읽다 실패한다 — 아무것도 저장되지 않는다
        verifyNoInteractions(goalService);
    }

    @Test
    void 이름이_비었거나_목표_금액이_없거나_0_이하면_400이다() throws Exception {
        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"targetAmount\":1000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\",\"targetAmount\":0}"))
                .andExpect(status().isBadRequest());

        // 누락은 0 이하와 다른 길이다 — @Positive는 null을 유효값으로 본다 (E-83)
        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(put("/goals/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    void 이름이_50자를_넘으면_400이다() throws Exception {
        String longName = "가".repeat(51);

        mockMvc.perform(put("/goals/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\",\"targetAmount\":1000000}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    void 삭제는_data_없이_성공만_돌려준다() throws Exception {
        mockMvc.perform(delete("/goals/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(goalService).delete(USER_ID, 3L);
    }

    @Test
    void 남의_목표는_404다() throws Exception {
        when(goalService.list(USER_ID)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(get("/goals"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
