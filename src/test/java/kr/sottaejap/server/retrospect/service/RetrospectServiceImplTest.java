package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.common.enums.ReflectionStep;
import kr.sottaejap.server.retrospect.dto.ReflectionDraft;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 저장 오케스트레이션 — 쓰기(커밋) → 명명 → 리프 재조회 순서와 후보 limit 기본값. */
@ExtendWith(MockitoExtension.class)
class RetrospectServiceImplTest {

    @Mock
    private RetrospectWriter retrospectWriter;
    @Mock
    private ClusterNamingService clusterNamingService;
    @Mock
    private CandidateService candidateService;
    @Mock
    private RetrospectChatSupport chatSupport;
    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private RetrospectRepository retrospectRepository;

    @InjectMocks
    private RetrospectServiceImpl service;

    @Test
    void 저장은_쓰기_명명_재조회_순서로_돌고_리프_묶음을_응답한다() {
        RetrospectSaveRequest request = new RetrospectSaveRequest(1043L, Satisfaction.LOW, "충동", "혼자", false, null);
        when(retrospectWriter.write(1L, request)).thenReturn(12L);
        BehaviorCluster leaf = BehaviorCluster.create(1L, "배달|NIGHT|충동|혼자");
        ReflectionTestUtils.setField(leaf, "id", 12L);
        leaf.apply(new ClusterEvaluation("배달|NIGHT|충동|혼자", null, 3, -1.0 / 3, -0.25, 12000, 36000,
                YearMonth.of(2026, 8), 3, 0.036, EvaluationStatus.RESOLVED, Quadrant.MINOR, Verdict.ADJUST,
                List.of(1L, 2L, 3L), List.of("배달의민족")), null);
        leaf.rename("심야 배달");
        when(behaviorClusterRepository.findById(12L)).thenReturn(Optional.of(leaf));

        RetrospectSaveResponse response = service.save(1L, request);

        InOrder order = inOrder(retrospectWriter, clusterNamingService, behaviorClusterRepository);
        order.verify(retrospectWriter).write(1L, request);
        order.verify(clusterNamingService).nameUnnamed(1L);
        order.verify(behaviorClusterRepository).findById(12L);
        assertEquals(12L, response.behaviorId());
        assertEquals("심야 배달", response.behaviorName());
        assertEquals(EvaluationStatus.RESOLVED, response.evaluationStatus());
        assertEquals(Verdict.ADJUST, response.verdict());
    }

    @Test
    void 쓰기가_실패하면_명명을_부르지_않는다() {
        RetrospectSaveRequest request = new RetrospectSaveRequest(1043L, Satisfaction.LOW, null, null, null, null);
        when(retrospectWriter.write(1L, request)).thenThrow(new BusinessException(CommonErrorCode.DUPLICATE_RETROSPECT));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(1L, request));

        assertEquals(CommonErrorCode.DUPLICATE_RETROSPECT, exception.getErrorCode());
        verify(clusterNamingService, never()).nameUnnamed(anyLong());
    }

    @Test
    void 후보_limit이_없으면_1이다() {
        when(candidateService.findCandidates(1L, 1, null, null)).thenReturn(List.of());

        assertEquals(0, service.candidates(1L, null, null, null).candidates().size());
        verify(candidateService).findCandidates(1L, 1, null, null);
    }

    @Test
    void 후보_limit_0이나_뒤집힌_기간은_INVALID_INPUT이다() {
        assertEquals(CommonErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.candidates(1L, 0, null, null)).getErrorCode());
        assertEquals(CommonErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.candidates(1L, 5, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 22))).getErrorCode());
        verifyNoInteractions(candidateService);
    }

    @Test
    void 대화_턴은_지원_컴포넌트에_위임한다() {
        RetrospectChatRequest request = new RetrospectChatRequest(1043L, null, null, null, null);
        RetrospectChatResponse expected = new RetrospectChatResponse("안녕하세요", ReflectionStep.SATISFACTION,
                ReflectionDraft.empty(), true, List.of("satisfaction"), false);
        when(chatSupport.chat(1L, request)).thenReturn(expected);

        assertEquals(expected, service.chat(1L, request));
    }
}
