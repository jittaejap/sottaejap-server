package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.BehaviorDetailResponse;
import kr.sottaejap.server.analysis.dto.BehaviorListResponse;
import kr.sottaejap.server.analysis.dto.BehaviorView;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.aggregate.ClusterOrderRule;
import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 묶음 목록 · 상세 (05 §2 `GET /behaviors`).
 *
 * <p>목록은 유효 묶음뿐이지만(E-72), <b>상세는 롤업된 리프도 연다</b>. 회고 저장 응답이 리프 id를 주기 때문에
 * 그 id로 상세를 열 수 없으면 저장 직후 화면이 막힌다. 대신 {@code parentId}를 같이 줘서 지도의 어느 점에
 * 해당하는지 알 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class BehaviorServiceImpl implements BehaviorService {

    private final BehaviorClusterRepository behaviorClusterRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public BehaviorListResponse behaviors(long userId) {
        List<BehaviorView> behaviors = behaviorClusterRepository.findEffectiveByUserId(userId).stream()
                .map(ClusterSnapshotMapper::toSnapshot)
                .sorted(ClusterOrderRule.mapOrder())
                .map(BehaviorView::from)
                .toList();
        return new BehaviorListResponse(behaviors);
    }

    @Override
    @Transactional(readOnly = true)
    public BehaviorDetailResponse behavior(long userId, long behaviorId) {
        BehaviorCluster cluster = behaviorClusterRepository.findByIdAndUserId(behaviorId, userId)
                .filter(found -> !found.isEmpty())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        List<TransactionAiView> transactions =
                transactionRepository.findAllByBehaviorIdInOrderByOccurredAtDescIdDesc(memberIds(cluster)).stream()
                        .map(TransactionAiView::from)
                        .toList();
        return new BehaviorDetailResponse(BehaviorView.from(ClusterSnapshotMapper.toSnapshot(cluster)), transactions);
    }

    /**
     * 이 묶음과 자식 리프의 id. 상위 묶음에는 직접 구성원만 배정돼 있어(E-59) 자식을 함께 읽지 않으면
     * 상세가 비어 보인다. 리프는 자식이 없으므로 자기 id 하나다.
     */
    private List<Long> memberIds(BehaviorCluster cluster) {
        List<Long> ids = new ArrayList<>();
        ids.add(cluster.getId());
        behaviorClusterRepository.findAllByParentId(cluster.getId())
                .forEach(child -> ids.add(child.getId()));
        return ids;
    }
}
