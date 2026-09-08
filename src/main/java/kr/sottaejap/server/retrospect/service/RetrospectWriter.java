package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.StandardTags;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 회고 저장 트랜잭션 (05 §2 POST /retrospects). 저장 → 사용자 전체 재계산까지가 한 트랜잭션이고,
 * 묶음 명명(⑤)은 커밋 후라 여기 없다 (E-64).
 *
 * <p>반환값은 그 거래의 <b>리프</b> 묶음 id다 (E-59) — 재계산이 {@code assignBehavior}로 채운 값을 그대로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class RetrospectWriter {

    private final TransactionRepository transactionRepository;
    private final RetrospectRepository retrospectRepository;
    private final ClusterRecomputeService clusterRecomputeService;
    private final Clock clock;

    @Transactional
    public Long write(long userId, RetrospectSaveRequest request) {
        validateTags(request);

        // 남의 거래와 없는 거래를 구분하지 않는다 — 둘 다 404다 (05 §2).
        Transaction transaction = transactionRepository.findByIdAndUserId(request.transactionId(), userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (retrospectRepository.existsByTransactionId(transaction.getId())) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT);
        }
        try {
            // 경쟁 저장은 retrospects.transaction_id UNIQUE가 잡는다. flush해야 여기서 예외를 받는다.
            retrospectRepository.saveAndFlush(Retrospect.completed(
                    transaction.getId(),
                    request.satisfaction(),
                    request.purpose(),
                    request.companion(),
                    request.repeatIntent(),
                    request.sourceOrDefault(),
                    OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException alreadyRetrospected) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT, alreadyRetrospected);
        }

        clusterRecomputeService.recomputeAll(userId);

        Long behaviorId = transaction.getBehaviorId();
        if (behaviorId == null) {
            throw new IllegalStateException("재계산 후 behaviorId가 비었다: transactionId=" + transaction.getId());
        }
        return behaviorId;
    }

    /** 목적·동행인은 표준 태그 7/6종 또는 null이다 (E-20). 자유 문자열은 저장하지 않는다. */
    private static void validateTags(RetrospectSaveRequest request) {
        if (request.purpose() != null && !StandardTags.isPurpose(request.purpose())) {
            throw new BusinessException(CommonErrorCode.INVALID_TAG);
        }
        if (request.companion() != null && !StandardTags.isCompanion(request.companion())) {
            throw new BusinessException(CommonErrorCode.INVALID_TAG);
        }
    }
}
