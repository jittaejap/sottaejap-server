package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.ClusterMemoryView;
import kr.sottaejap.server.retrospect.dto.MemoryResponse;
import kr.sottaejap.server.retrospect.dto.ReflectionView;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * /retrospects/* 진입점. {@link #save}는 일부러 @Transactional이 아니다 — 쓰기({@link RetrospectWriter})를
 * 커밋한 뒤 AI 명명({@link ClusterNamingService})을 부르므로 DB 트랜잭션이 AI 타임아웃(15초) 동안 열려 있지 않다 (E-64).
 */
@Service
@RequiredArgsConstructor
public class RetrospectServiceImpl implements RetrospectService {

    /** 내부 AI get_memory의 recentReflections 개수 — 규칙 값이 아니라 응답 크기다. */
    private static final int RECENT_REFLECTION_LIMIT = 10;
    private static final int DEFAULT_CANDIDATE_LIMIT = 1;

    private final RetrospectWriter retrospectWriter;
    private final ClusterNamingService clusterNamingService;
    private final CandidateService candidateService;
    private final RetrospectChatSupport chatSupport;
    private final BehaviorClusterRepository behaviorClusterRepository;
    private final RetrospectRepository retrospectRepository;

    @Override
    public RetrospectSaveResponse save(long userId, RetrospectSaveRequest request) {
        Long leafId = retrospectWriter.write(userId, request);
        clusterNamingService.nameUnnamed(userId);
        BehaviorCluster leaf = behaviorClusterRepository.findById(leafId)
                .orElseThrow(() -> new IllegalStateException("재계산 직후 리프 묶음이 없다: " + leafId));
        return RetrospectSaveResponse.from(leaf);
    }

    @Override
    public CandidateListResponse candidates(long userId, Integer limit, LocalDate from, LocalDate to) {
        int resolvedLimit = limit == null ? DEFAULT_CANDIDATE_LIMIT : limit;
        if (resolvedLimit < 1 || (from != null && to != null && from.isAfter(to))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new CandidateListResponse(candidateService.findCandidates(userId, resolvedLimit, from, to));
    }

    @Override
    public RetrospectChatResponse chat(long userId, RetrospectChatRequest request) {
        return chatSupport.chat(userId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReflectionView> findReflections(long userId) {
        return retrospectRepository.findAllWithTransactionByUserId(userId).stream()
                .map(RetrospectWithTransaction::retrospect)
                .map(ReflectionView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MemoryResponse memory(long userId) {
        List<ClusterMemoryView> clusters = behaviorClusterRepository.findAllByUserIdOrderByClusterKeyAsc(userId).stream()
                .filter(cluster -> !cluster.isEmpty())
                .map(ClusterMemoryView::from)
                .toList();
        List<ReflectionView> recent = retrospectRepository
                .findRecentWithTransactionByUserId(userId, PageRequest.of(0, RECENT_REFLECTION_LIMIT)).stream()
                .map(RetrospectWithTransaction::retrospect)
                .map(ReflectionView::from)
                .toList();
        return new MemoryResponse(clusters, recent);
    }
}
