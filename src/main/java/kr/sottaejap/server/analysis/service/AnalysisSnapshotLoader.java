package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import kr.sottaejap.server.transaction.service.TransactionService;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 분석에 필요한 것을 한 번에 읽는다. 트랜잭션은 여기서 열고 여기서 닫는다. */
@Component
@RequiredArgsConstructor
public class AnalysisSnapshotLoader {

    private final BehaviorClusterRepository behaviorClusterRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    public AnalysisSnapshot load(long userId) {
        Integer monthlyBudget = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getMonthlyBudget();
        List<ClusterSnapshot> clusters = behaviorClusterRepository.findEffectiveByUserId(userId).stream()
                .map(ClusterSnapshotMapper::toSnapshot)
                .toList();
        return new AnalysisSnapshot(transactionService.analysisYearMonth(userId), monthlyBudget, clusters);
    }
}
