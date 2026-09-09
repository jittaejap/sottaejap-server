package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.dto.TransactionListQuery;
import kr.sottaejap.server.transaction.dto.TransactionListResponse;
import kr.sottaejap.server.transaction.parser.TransactionFileParser;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 거래 목록 {@code list} (05 §2 · E-93). 기간 · 카테고리 · 회고 여부 · 남의 거래 제외는 질의가 거르고
 * ({@code TransactionRepositorySearchTest}), 여기서는 쿼리를 어떻게 바꿔 넘기고 결과를 어떻게 꾸미는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final long USER_ID = 1L;
    private static final OffsetDateTime NIGHT_DELIVERY = OffsetDateTime.parse("2026-08-22T23:10:00+09:00");
    private static final OffsetDateTime MORNING_CAFE = OffsetDateTime.parse("2026-08-23T08:15:00+09:00");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionFileParser parser;
    @Mock
    private RetrospectRepository retrospectRepository;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(transactionRepository, parser, retrospectRepository);
    }

    @Test
    void 회고가_있는_거래에는_요약을_싣고_없는_거래는_null이다() {
        Transaction delivery = transaction(1043L, NIGHT_DELIVERY, "배달");
        Transaction cafe = transaction(1044L, MORNING_CAFE, "카페");
        when(transactionRepository.search(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(cafe, delivery), PageRequest.of(0, 20), 2));
        when(retrospectRepository.findAllByTransactionIdIn(List.of(1044L, 1043L)))
                .thenReturn(List.of(retrospect(77L, 1043L, Satisfaction.LOW)));

        TransactionListResponse response = transactionService.list(USER_ID, query(null, null, null, null, 0, 20));

        assertEquals(2, response.transactions().size());
        assertEquals(1044L, response.transactions().get(0).id());
        assertNull(response.transactions().get(0).retrospectId());
        assertNull(response.transactions().get(0).satisfaction());
        assertEquals(1043L, response.transactions().get(1).id());
        assertEquals(77L, response.transactions().get(1).retrospectId());
        assertEquals(Satisfaction.LOW, response.transactions().get(1).satisfaction());
        // 저장된 UTC 시각은 +09:00으로 되돌린다 (05 §0)
        assertEquals("2026-08-22T23:10+09:00", response.transactions().get(1).occurredAt().toString());
        assertEquals(TimeSlot.NIGHT, response.transactions().get(1).timeSlot());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void 빈_결과는_회고를_읽지_않고_빈_목록이다() {
        when(transactionRepository.search(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        TransactionListResponse response = transactionService.list(USER_ID, query(null, null, null, null, 0, 20));

        assertTrue(response.transactions().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        verifyNoInteractions(retrospectRepository);
    }

    @Test
    void 날짜는_KST_자정으로_바꾸고_to는_다음_날_0시_미만이며_빈_카테고리는_조건_없음이다() {
        when(transactionRepository.search(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        transactionService.list(USER_ID, query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), " ", true, 0, 20));

        verify(transactionRepository).search(USER_ID,
                OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                null, true, PageRequest.of(0, 20));
    }

    @Test
    void size가_100을_넘으면_100으로_자르고_응답의_size도_100이다() {
        when(transactionRepository.search(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(3, 100)));

        TransactionListResponse response = transactionService.list(USER_ID, query(null, null, "배달", null, 3, 500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).search(eq(USER_ID), isNull(), isNull(), eq("배달"), isNull(), pageable.capture());
        assertEquals(PageRequest.of(3, 100), pageable.getValue());
        assertEquals(100, response.size());
        assertEquals(3, response.page());
    }

    @Test
    void size가_1_미만이면_400이다() {
        assertInvalid(query(null, null, null, null, 0, 0));
    }

    @Test
    void page가_음수이면_400이다() {
        assertInvalid(query(null, null, null, null, -1, 20));
    }

    @Test
    void from이_to보다_뒤이면_400이다() {
        assertInvalid(query(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), null, null, 0, 20));
    }

    /** 05 §2 행수 상한 — 파서의 신호를 400 TOO_MANY_ROWS로 바꾸고, 저장소는 건드리지 않는다 (06 R28). */
    @Test
    void 행수_상한을_넘긴_파일은_400_TOO_MANY_ROWS이고_한_건도_저장하지_않는다() {
        when(parser.parseCsv(any())).thenThrow(TransactionFileParser.TooManyRowsException.counted(TransactionFileParser.MAX_ROWS + 1));
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", "거래일시,가맹점명,금액\n".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> transactionService.upload(USER_ID, file));

        assertEquals(CommonErrorCode.TOO_MANY_ROWS, exception.getErrorCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void findForAi는_같은_질의를_회고_조건_없이_쓴다() {
        when(transactionRepository.search(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(transaction(1043L, NIGHT_DELIVERY, "배달"))));

        assertEquals(1043L, transactionService.findForAi(USER_ID, null, null, null, null).get(0).id());

        verify(transactionRepository).search(USER_ID, null, null, null, null, PageRequest.of(0, 100));
        verifyNoInteractions(retrospectRepository);
    }

    private void assertInvalid(TransactionListQuery query) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> transactionService.list(USER_ID, query));

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(transactionRepository, retrospectRepository);
    }

    private static TransactionListQuery query(LocalDate from, LocalDate to, String category, Boolean hasRetrospect,
                                              int page, int size) {
        return new TransactionListQuery(from, to, category, hasRetrospect, page, size);
    }

    private static Transaction transaction(long id, OffsetDateTime occurredAt, String category) {
        // DB에서 읽은 것처럼 UTC로 정규화된 시각을 준다
        Transaction transaction = Transaction.of(USER_ID, occurredAt.withOffsetSameInstant(java.time.ZoneOffset.UTC),
                "가맹점", 12000, category, category, "hash-" + id);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static Retrospect retrospect(long id, long transactionId, Satisfaction satisfaction) {
        Retrospect retrospect = Retrospect.completed(transactionId, satisfaction, null, null, null,
                RetrospectSource.CANDIDATE, OffsetDateTime.now());
        ReflectionTestUtils.setField(retrospect, "id", id);
        return retrospect;
    }
}
