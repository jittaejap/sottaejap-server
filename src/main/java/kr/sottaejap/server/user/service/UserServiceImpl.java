package kr.sottaejap.server.user.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.dto.UserMeResponse;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserMeResponse getMe(long userId) {
        return userRepository.findById(userId)
                .map(user -> UserMeResponse.from(user, analysisYearMonth(userId)))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 최근 거래월(KST) — 모든 묶음이 같은 달을 쓴다 (E-60). 거래가 없으면 null. */
    private String analysisYearMonth(long userId) {
        return transactionRepository.findTopByUserIdOrderByOccurredAtDesc(userId)
                .map(transaction -> YearMonth.from(transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE)).toString())
                .orElse(null);
    }
}
