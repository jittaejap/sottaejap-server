package kr.sottaejap.server.transaction.controller;

import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.support.FixedPrincipalResolver;
import kr.sottaejap.server.transaction.dto.TransactionListQuery;
import kr.sottaejap.server.transaction.dto.TransactionListResponse;
import kr.sottaejap.server.transaction.dto.TransactionView;
import kr.sottaejap.server.transaction.service.TransactionService;
import kr.sottaejap.server.transaction.service.TransactionUploadFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** `GET /transactions` HTTP 계약 (05 §2 #7 · E-93). 업로드는 {@code TransactionUploadFacadeTest}가 본다. */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    private static final long USER_ID = 1L;

    @Mock
    private TransactionUploadFacade transactionUploadFacade;
    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionUploadFacade, transactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedPrincipalResolver(USER_ID))
                .build();
    }

    @Test
    void 응답은_봉투_안의_object이고_05의_필드를_그대로_싣는다() throws Exception {
        when(transactionService.list(eq(USER_ID), any())).thenReturn(new TransactionListResponse(List.of(
                new TransactionView(1043L, OffsetDateTime.parse("2026-08-22T23:10:00+09:00"), "○○배달", 12000,
                        "배달", TimeSlot.NIGHT, 77L, Satisfaction.LOW),
                new TransactionView(1044L, OffsetDateTime.parse("2026-08-23T08:15:00+09:00"), "○○카페", 4500,
                        "카페", TimeSlot.MORNING, null, null)),
                0, 20, 143, 8));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactions[0].id").value(1043))
                .andExpect(jsonPath("$.data.transactions[0].occurredAt").value("2026-08-22T23:10:00+09:00"))
                .andExpect(jsonPath("$.data.transactions[0].merchant").value("○○배달"))
                .andExpect(jsonPath("$.data.transactions[0].amount").value(12000))
                .andExpect(jsonPath("$.data.transactions[0].category").value("배달"))
                .andExpect(jsonPath("$.data.transactions[0].timeSlot").value("NIGHT"))
                .andExpect(jsonPath("$.data.transactions[0].retrospectId").value(77))
                .andExpect(jsonPath("$.data.transactions[0].satisfaction").value("LOW"))
                .andExpect(jsonPath("$.data.transactions[1].retrospectId").value((Object) null))
                .andExpect(jsonPath("$.data.transactions[1].satisfaction").value((Object) null))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(143))
                .andExpect(jsonPath("$.data.totalPages").value(8));
    }

    @Test
    void 파라미터를_생략하면_page_0_size_20이고_필터는_null이다() throws Exception {
        when(transactionService.list(eq(USER_ID), any()))
                .thenReturn(new TransactionListResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions").isEmpty());

        verify(transactionService).list(USER_ID, new TransactionListQuery(null, null, null, null, 0, 20));
    }

    @Test
    void 필터와_페이징을_그대로_넘긴다() throws Exception {
        when(transactionService.list(eq(USER_ID), any()))
                .thenReturn(new TransactionListResponse(List.of(), 2, 50, 0, 0));

        mockMvc.perform(get("/transactions")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("category", "배달")
                        .param("hasRetrospect", "true")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(transactionService).list(USER_ID, new TransactionListQuery(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "배달", true, 2, 50));
    }

    @Test
    void 타입이_맞지_않는_값은_400_INVALID_INPUT이다() throws Exception {
        mockMvc.perform(get("/transactions").param("from", "2026-8-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/transactions").param("size", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/transactions").param("hasRetrospect", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void 서비스가_거부한_요청은_400_봉투다() throws Exception {
        when(transactionService.list(eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/transactions").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
