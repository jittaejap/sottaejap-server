package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.ErrorCode;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 회고 저장 트랜잭션 (05 §2 POST /retrospects). 태그 검증 → 소유 확인 → 중복 → 저장 → 재계산 순서를 지킨다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectWriterTest {

    private static final long USER_ID = 7L;
    private static final long TRANSACTION_ID = 1043L;
    private static final long BEHAVIOR_ID = 55L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), TimeSlot.ZONE);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RetrospectRepository retrospectRepository;
    @Mock
    private ClusterRecomputeService clusterRecomputeService;

    private RetrospectWriter writer;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        writer = new RetrospectWriter(transactionRepository, retrospectRepository, clusterRecomputeService, CLOCK);
        transaction = Transaction.of(USER_ID, OffsetDateTime.parse("2026-08-23T13:30:00Z"),
                "배달의민족", 12000, "배달", "배달", "hash");
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
    }

    @Test
    void 저장하면_재계산이_배정한_리프_묶음_id를_돌려준다() {
        givenOwnedTransaction();
        givenNoRetrospect();
        givenRecomputeAssignsBehavior();

        Long behaviorId = writer.write(USER_ID, request("충동", "혼자", RetrospectSource.MANUAL));

        assertEquals(BEHAVIOR_ID, behaviorId.longValue());
        verify(clusterRecomputeService).recomputeAll(USER_ID);
    }

    @Test
    void 표준_태그가_아닌_목적은_400_INVALID_TAG다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> writer.write(USER_ID, request("야식", "혼자", RetrospectSource.MANUAL)));

        assertErrorCode(CommonErrorCode.INVALID_TAG, exception);
        // 태그는 조회 전에 거른다.
        verifyNoInteractions(transactionRepository, retrospectRepository, clusterRecomputeService);
    }

    @Test
    void 표준_태그가_아닌_동행인도_400_INVALID_TAG다() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> writer.write(USER_ID, request("충동", "반려견", RetrospectSource.MANUAL)));

        assertErrorCode(CommonErrorCode.INVALID_TAG, exception);
    }

    @Test
    void 가운뎃점_둘레_공백은_정본_표기로_저장한다() {
        givenOwnedTransaction();
        givenNoRetrospect();
        givenRecomputeAssignsBehavior();

        // 화면 문구는 "휴식 · 취미"지만 정본은 "휴식·취미"다. 원문을 그대로 저장하면 같은 태그가 두 묶음으로 갈라진다.
        writer.write(USER_ID, request("휴식 · 취미", " 혼자 ", RetrospectSource.ONBOARDING));

        Retrospect saved = captureSaved();
        assertEquals("휴식·취미", saved.getPurpose());
        assertEquals("혼자", saved.getCompanion());
    }

    @Test
    void 목적과_동행인이_null이면_미확정으로_저장한다() {
        givenOwnedTransaction();
        givenNoRetrospect();
        givenRecomputeAssignsBehavior();

        writer.write(USER_ID, request(null, null, RetrospectSource.MANUAL));

        Retrospect saved = captureSaved();
        assertNull(saved.getPurpose());
        assertNull(saved.getCompanion());
    }

    @Test
    void 남의_거래는_404_NOT_FOUND다() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> writer.write(USER_ID, request("충동", "혼자", RetrospectSource.MANUAL)));

        assertErrorCode(CommonErrorCode.NOT_FOUND, exception);
        verify(retrospectRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_회고한_거래는_409_DUPLICATE_RETROSPECT다() {
        givenOwnedTransaction();
        when(retrospectRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> writer.write(USER_ID, request("충동", "혼자", RetrospectSource.MANUAL)));

        assertErrorCode(CommonErrorCode.DUPLICATE_RETROSPECT, exception);
        verify(retrospectRepository, never()).saveAndFlush(any());
        verify(clusterRecomputeService, never()).recomputeAll(anyLong());
    }

    @Test
    void 경쟁_저장의_UNIQUE_위반도_409_DUPLICATE_RETROSPECT다() {
        givenOwnedTransaction();
        givenNoRetrospect();
        when(retrospectRepository.saveAndFlush(any(Retrospect.class)))
                .thenThrow(new DataIntegrityViolationException("uq_retrospects_transaction"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> writer.write(USER_ID, request("충동", "혼자", RetrospectSource.MANUAL)));

        assertErrorCode(CommonErrorCode.DUPLICATE_RETROSPECT, exception);
        verify(clusterRecomputeService, never()).recomputeAll(anyLong());
    }

    @Test
    void source가_없으면_CANDIDATE로_저장한다() {
        givenOwnedTransaction();
        givenNoRetrospect();
        givenRecomputeAssignsBehavior();

        writer.write(USER_ID, request("충동", "혼자", null));

        Retrospect saved = captureSaved();
        assertEquals(RetrospectSource.CANDIDATE, saved.getSource());
        assertEquals(OffsetDateTime.now(CLOCK), saved.getCreatedAt());
    }

    private void givenOwnedTransaction() {
        when(transactionRepository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(transaction));
    }

    private void givenNoRetrospect() {
        when(retrospectRepository.existsByTransactionId(TRANSACTION_ID)).thenReturn(false);
    }

    /** 재계산이 assignBehavior로 behaviorId를 채운다 (E-59). 반환값이 아니라 그 부작용을 읽는다. */
    private void givenRecomputeAssignsBehavior() {
        doAnswer(invocation -> {
            transaction.assignBehavior(BEHAVIOR_ID);
            return List.of();
        }).when(clusterRecomputeService).recomputeAll(USER_ID);
    }

    private Retrospect captureSaved() {
        ArgumentCaptor<Retrospect> captor = ArgumentCaptor.forClass(Retrospect.class);
        verify(retrospectRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static RetrospectSaveRequest request(String purpose, String companion, RetrospectSource source) {
        return new RetrospectSaveRequest(TRANSACTION_ID, Satisfaction.LOW, purpose, companion, Boolean.FALSE, source);
    }

    private static void assertErrorCode(ErrorCode expected, BusinessException exception) {
        assertEquals(expected, exception.getErrorCode());
    }
}
