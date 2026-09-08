package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.retrospect.domain.BehaviorCluster;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.rules.cluster.ClusterEvaluation;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.dto.SuggestionAdoptRequest;
import kr.sottaejap.server.suggestion.dto.SuggestionView;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 제안 목록 · 채택 · 거절 (05 §2 #15 · #16 · E-82). */
@ExtendWith(MockitoExtension.class)
class SuggestionServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private SuggestionRepository suggestionRepository;
    @Mock
    private BehaviorClusterRepository behaviorClusterRepository;
    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private SuggestionServiceImpl service;

    @Test
    void 기본_목록은_거절한_제안을_뺀다() {
        Suggestion rejected = suggestion(3L, 2L);
        rejected.reject();
        givenSuggestions(List.of(suggestion(1L, 1L), rejected));
        givenClusters(cluster(1L, Quadrant.MINOR, "심야 배달"), cluster(2L, Quadrant.MINOR, "낮 카페"));

        List<SuggestionView> views = service.list(USER_ID, null).suggestions();

        assertEquals(List.of(1L), views.stream().map(SuggestionView::id).toList());
    }

    @Test
    void status를_주면_그_상태만_본다() {
        Suggestion rejected = suggestion(3L, 2L);
        rejected.reject();
        givenSuggestions(List.of(suggestion(1L, 1L), rejected));
        givenClusters(cluster(1L, Quadrant.MINOR, "심야 배달"), cluster(2L, Quadrant.MINOR, "낮 카페"));

        List<SuggestionView> views = service.list(USER_ID, SuggestionStatus.REJECTED).suggestions();

        assertEquals(List.of(3L), views.stream().map(SuggestionView::id).toList());
    }

    @Test
    void 부담이_큰_좌표부터_보여준다() {
        givenSuggestions(List.of(suggestion(1L, 1L), suggestion(2L, 2L)));
        givenClusters(cluster(1L, Quadrant.MINOR, "심야 배달"), cluster(2L, Quadrant.PRIORITY, "택시"));

        List<SuggestionView> views = service.list(USER_ID, null).suggestions();

        assertEquals(List.of(2L, 1L), views.stream().map(SuggestionView::id).toList());
    }

    /** 04 §3의 마지막 동점 기준은 묶음 키가 아니라 제안 id다. 여기서는 둘의 순서가 서로 반대다. */
    @Test
    void 좌표와_부담이_같으면_묶음_키가_아니라_제안_id_순이다() {
        givenSuggestions(List.of(suggestion(2L, 2L), suggestion(1L, 1L)));
        givenClusters(cluster(1L, Quadrant.MINOR, "낮 카페", "카페|DAY||"),
                cluster(2L, Quadrant.MINOR, "심야 배달", "배달|NIGHT||"));

        List<SuggestionView> views = service.list(USER_ID, null).suggestions();

        assertEquals(List.of(1L, 2L), views.stream().map(SuggestionView::id).toList());
    }

    @Test
    void 이름이_없으면_묶음_키로_만든_이름을_쓰고_이유가_붙는다() {
        givenSuggestions(List.of(suggestion(1L, 1L)));
        givenClusters(cluster(1L, Quadrant.PRIORITY, null));

        SuggestionView view = service.list(USER_ID, null).suggestions().getFirst();

        assertEquals("심야 배달", view.behaviorName());
        assertTrue(view.reason().contains("심야 배달"), view.reason());
        assertTrue(view.reason().contains("96,000"), view.reason());
    }

    @Test
    void 채택하면_절감액이_계산되고_목표에_붙는다() {
        Suggestion suggestion = suggestion(1L, 1L);
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(5L, USER_ID))
                .thenReturn(Optional.of(Goal.create(USER_ID, "여행", 1_000_000, 0)));

        SuggestionView view = service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(2, 5L));

        assertEquals(SuggestionStatus.ADOPTED, view.status());
        assertEquals(24_000, view.expectedSaving());
        assertEquals(5L, view.goalId());
    }

    @Test
    void 목표_없이도_채택할_수_있다() {
        Suggestion suggestion = suggestion(1L, 1L);
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));

        SuggestionView view = service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(1, null));

        assertNull(view.goalId());
        assertEquals(12_000, view.expectedSaving());
    }

    @Test
    void 조정_횟수가_거래_건수를_넘으면_400이다() {
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion(1L, 1L)));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(9, null)));
        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void 남의_목표에_붙이려_하면_404다() {
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion(1L, 1L)));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(99L, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(1, 99L)));
        assertEquals(CommonErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void 이미_채택한_제안은_횟수를_고칠_수_있다() {
        Suggestion suggestion = suggestion(1L, 1L);
        suggestion.adopt(2, 24_000, null);
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));

        SuggestionView view = service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(4, null));

        assertEquals(48_000, view.expectedSaving());
    }

    @Test
    void 거절한_제안은_다시_채택할_수도_거절할_수도_없다() {
        Suggestion rejected = suggestion(1L, 1L);
        rejected.reject();
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(rejected));

        assertEquals(CommonErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.adopt(USER_ID, 1L, new SuggestionAdoptRequest(1, null))).getErrorCode());
        assertEquals(CommonErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.reject(USER_ID, 1L)).getErrorCode());
    }

    @Test
    void 채택한_제안도_철회할_수_있다() {
        Suggestion suggestion = suggestion(1L, 1L);
        suggestion.adopt(2, 24_000, null);
        when(suggestionRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(suggestion));
        when(behaviorClusterRepository.findById(1L)).thenReturn(Optional.of(cluster(1L, Quadrant.MINOR, "심야 배달")));

        assertEquals(SuggestionStatus.REJECTED, service.reject(USER_ID, 1L).status());
    }

    @Test
    void 없는_제안과_남의_제안은_구별하지_않고_404다() {
        when(suggestionRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.reject(USER_ID, 99L)).getErrorCode());
    }

    private void givenSuggestions(List<Suggestion> suggestions) {
        when(suggestionRepository.findAllByUserId(USER_ID)).thenReturn(suggestions);
    }

    private void givenClusters(BehaviorCluster... clusters) {
        when(behaviorClusterRepository.findAllById(any())).thenReturn(List.of(clusters));
    }

    private static Suggestion suggestion(long id, long behaviorId) {
        Suggestion suggestion = Suggestion.propose(behaviorId, 8, 96_000);
        ReflectionTestUtils.setField(suggestion, "id", id);
        return suggestion;
    }

    private static BehaviorCluster cluster(long id, Quadrant quadrant, String displayName) {
        return cluster(id, quadrant, displayName,
                quadrant == Quadrant.PRIORITY ? "배달|NIGHT|충동|혼자" : "배달|NIGHT||");
    }

    private static BehaviorCluster cluster(long id, Quadrant quadrant, String displayName, String key) {
        BehaviorCluster cluster = BehaviorCluster.create(USER_ID, key);
        cluster.apply(new ClusterEvaluation(key, null, 4, -0.5, -0.42, 12_000, 96_000,
                YearMonth.of(2026, 8), 8, quadrant == Quadrant.PRIORITY ? 0.3 : 0.096,
                EvaluationStatus.RESOLVED, quadrant, Verdict.ADJUST, List.of(), List.of()), null);
        ReflectionTestUtils.setField(cluster, "id", id);
        if (displayName != null) {
            cluster.rename(displayName);
        }
        return cluster;
    }
}
