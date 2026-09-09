package kr.sottaejap.server.user.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
import kr.sottaejap.server.transaction.service.TransactionService;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.dto.UserMeResponse;
import kr.sottaejap.server.user.dto.UserSettingsRequest;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final ClusterRecomputeService clusterRecomputeService;

    @Override
    @Transactional(readOnly = true)
    public UserMeResponse getMe(long userId) {
        return userRepository.findById(userId)
                .map(user -> UserMeResponse.from(user, analysisYearMonth(userId)))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 예산이 바뀌면 <b>묶음을 다시 계산한다</b> (E-77 · FR-06-06). {@code burdenRatio}·{@code quadrant}는 재계산이
     * 묶음 행에 써 둔 값이라, 예산만 고치면 지도의 가로축과 처방·CTA가 옛 예산 기준으로 남는다.
     *
     * <p><b>{@code outlierBaseAmount}는 재계산을 부르지 않는다</b> (E-115). 후보는 조회 시점에 계산하므로(E-62)
     * 묶음 행에 굳은 값이 없다 — 다음 {@code GET /retrospects/candidates}부터 새 기준이 적용된다.
     */
    @Override
    @Transactional
    public UserMeResponse updateSettings(long userId, UserSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        boolean budgetChanged = request.monthlyBudget() != null
                && !Objects.equals(user.getMonthlyBudget(), request.monthlyBudget());
        user.updateSettings(request.monthlyBudget(), request.outlierThreshold(),
                request.outlierBaseAmount(), request.retrospectDelayDays());
        if (budgetChanged) {
            clusterRecomputeService.recomputeAll(userId);
        }
        return UserMeResponse.from(user, analysisYearMonth(userId));
    }

    /** 최근 거래월(KST) — 산출은 TransactionService 하나가 한다 (E-60 · E-78). 거래가 없으면 null. */
    private String analysisYearMonth(long userId) {
        return Optional.ofNullable(transactionService.analysisYearMonth(userId))
                .map(YearMonth::toString)
                .orElse(null);
    }
}
