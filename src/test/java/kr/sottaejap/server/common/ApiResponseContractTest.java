package kr.sottaejap.server.common;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import kr.sottaejap.server.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 05 §0 봉투 고정: 성공은 data만, 실패는 error{code,message}만. 날짜는 ISO 8601 오프셋 문자열.
 */
class ApiResponseContractTest {

    private MockMvc mockMvc;

    @RestController
    static class FixtureController {

        @GetMapping("/fixture/ok")
        ApiResponse<Map<String, Object>> ok() {
            return ApiResponse.success(Map.of(
                    "occurredAt", OffsetDateTime.of(2026, 8, 22, 23, 10, 0, 0, ZoneOffset.ofHours(9))));
        }

        @GetMapping("/fixture/duplicate")
        ApiResponse<Void> duplicate() {
            throw new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT);
        }

        @GetMapping("/fixture/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("internal detail must not leak");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void success_hasDataOnly_andIsoOffsetDate() throws Exception {
        mockMvc.perform(get("/fixture/ok"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-22T23:10:00+09:00"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void businessException_mapsToErrorCodeAndStatus() throws Exception {
        mockMvc.perform(get("/fixture/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RETROSPECT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedException_hidesInternalMessage() throws Exception {
        mockMvc.perform(get("/fixture/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
