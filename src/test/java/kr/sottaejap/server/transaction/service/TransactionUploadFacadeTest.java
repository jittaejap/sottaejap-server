package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 업로드 뒤 재계산의 실패 처리와 건너뜀 조건 (E-95 · 06 R24). */
@ExtendWith(MockitoExtension.class)
class TransactionUploadFacadeTest {

    private static final long USER_ID = 7L;

    private final MultipartFile file = new MockMultipartFile("file", "aug.csv", "text/csv",
            "거래일자,가맹점명,거래금액\n2026-08-25 20:22,○○마트,12000\n".getBytes(StandardCharsets.UTF_8));

    @Mock
    private TransactionService transactionService;
    @Mock
    private ClusterRecomputeService clusterRecomputeService;

    @InjectMocks
    private TransactionUploadFacade facade;

    @Test
    void 거래가_들어오면_묶음을_다시_계산한다() {
        when(transactionService.upload(USER_ID, file)).thenReturn(uploaded(1));

        facade.upload(USER_ID, file);

        verify(clusterRecomputeService).recomputeAll(USER_ID);
    }

    /** 전량 중복 재업로드는 기준월도 금액도 바꾸지 않는다 — 전체 재계산을 돌릴 이유가 없다. */
    @Test
    void 새로_저장된_거래가_없으면_재계산을_부르지_않는다() {
        when(transactionService.upload(USER_ID, file)).thenReturn(uploaded(0));

        facade.upload(USER_ID, file);

        verify(clusterRecomputeService, never()).recomputeAll(anyLong());
    }

    /**
     * 재계산이 실패해도 업로드는 성공으로 응답한다 (E-95 ④). 업로드는 사용자가 파일을 고르고 기다리는 행위라
     * 성공률이 응답 속도보다 앞선다 — 어긋난 값은 다음 회고 저장이나 예산 변경의 재계산이 덮는다.
     */
    @Test
    void 재계산이_실패해도_업로드_응답을_그대로_돌려준다() {
        when(transactionService.upload(USER_ID, file)).thenReturn(uploaded(1));
        when(clusterRecomputeService.recomputeAll(USER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

        TransactionUploadResponse response = facade.upload(USER_ID, file);

        assertEquals(1, response.importedCount());
        assertEquals(LocalDate.of(2026, 8, 25), response.periodFrom());
    }

    /**
     * 재계산은 업로드를 커밋한 뒤에 돈다 (E-95 ③). 파사드에 {@code @Transactional}이 붙으면 업로드와 재계산이
     * 한 트랜잭션이 되어 결정이 뒤집힌다 — 재계산 실패가 파싱에 성공한 CSV까지 되돌린다.
     */
    @Test
    void 파사드는_트랜잭션을_열지_않는다() {
        assertNull(TransactionUploadFacade.class.getAnnotation(Transactional.class));
        for (Method method : TransactionUploadFacade.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(Transactional.class), method.getName());
        }
    }

    /** 저장된 거래가 없으면 기간 경계도 null이다 — 실제 응답과 같은 모양으로 세운다. */
    private static TransactionUploadResponse uploaded(int importedCount) {
        LocalDate day = importedCount == 0 ? null : LocalDate.of(2026, 8, 25);
        return new TransactionUploadResponse(importedCount, 0, day, day, List.of());
    }
}
