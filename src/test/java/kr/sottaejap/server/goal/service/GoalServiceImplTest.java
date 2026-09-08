package kr.sottaejap.server.goal.service;

import kr.sottaejap.server.common.enums.SuggestionStatus;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.goal.domain.Goal;
import kr.sottaejap.server.goal.dto.GoalRequest;
import kr.sottaejap.server.goal.dto.GoalView;
import kr.sottaejap.server.goal.repository.GoalRepository;
import kr.sottaejap.server.suggestion.domain.Suggestion;
import kr.sottaejap.server.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 목표 4종 (05 §2 · E-83). */
@ExtendWith(MockitoExtension.class)
class GoalServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private SuggestionRepository suggestionRepository;

    private GoalServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC);
        service = new GoalServiceImpl(goalRepository, suggestionRepository, clock);
    }

    @Test
    void 목록은_채택한_제안의_절감액을_합산해_전망을_낸다() {
        when(goalRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID))
                .thenReturn(List.of(goal(3L, 1_000_000, 250_000)));
        when(suggestionRepository.findAllByGoalIdInAndStatus(anyList(), eq(SuggestionStatus.ADOPTED)))
                .thenReturn(List.of(adopted(3L, 24_000), adopted(3L, 12_000)));

        GoalView view = service.list(USER_ID).goals().getFirst();

        assertEquals(36_000, view.adoptedSaving());
        assertEquals(0.25, view.achievementRate());
        assertEquals(0.286, view.projectedRate(), 1e-9);
    }

    @Test
    void 채택한_제안이_없으면_절감액은_0이고_전망은_달성률과_같다() {
        when(goalRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID))
                .thenReturn(List.of(goal(3L, 1_000_000, 0)));
        when(suggestionRepository.findAllByGoalIdInAndStatus(anyList(), eq(SuggestionStatus.ADOPTED))).thenReturn(List.of());

        GoalView view = service.list(USER_ID).goals().getFirst();

        assertEquals(0, view.adoptedSaving());
        assertEquals(0.0, view.achievementRate());
        assertEquals(0.0, view.projectedRate());
    }

    @Test
    void 목표가_없으면_제안을_조회하지도_않는다() {
        when(goalRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID)).thenReturn(List.of());

        assertEquals(List.of(), service.list(USER_ID).goals());
    }

    @Test
    void 목표_금액이_0이면_비율이_null이다() {
        when(goalRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID))
                .thenReturn(List.of(goal(3L, 0, 0)));
        when(suggestionRepository.findAllByGoalIdInAndStatus(anyList(), eq(SuggestionStatus.ADOPTED))).thenReturn(List.of());

        GoalView view = service.list(USER_ID).goals().getFirst();

        assertNull(view.achievementRate());
        assertNull(view.projectedRate());
    }

    @Test
    void 삭제는_soft이고_지운_뒤에는_404다() {
        Goal goal = goal(3L, 1_000_000, 0);
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(3L, USER_ID))
                .thenReturn(Optional.of(goal), Optional.empty());

        service.delete(USER_ID, 3L);

        assertTrue(goal.isDeleted());
        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.delete(USER_ID, 3L)).getErrorCode());
    }

    @Test
    void 수정은_이름과_목표액을_바꾸고_실적은_생략하면_유지한다() {
        Goal goal = goal(3L, 1_000_000, 250_000);
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(3L, USER_ID)).thenReturn(Optional.of(goal));
        when(suggestionRepository.findAllByGoalIdInAndStatus(anyList(), eq(SuggestionStatus.ADOPTED))).thenReturn(List.of());

        service.update(USER_ID, 3L, new GoalRequest("새 이름", 2_000_000, null));

        assertEquals("새 이름", goal.getName());
        assertEquals(2_000_000, goal.getTargetAmount());
        assertEquals(250_000, goal.getCurrentAmount());
    }

    @Test
    void 남의_목표는_404다() {
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(99L, USER_ID)).thenReturn(Optional.empty());

        assertEquals(CommonErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.update(USER_ID, 99L, new GoalRequest("이름", 1, 0))).getErrorCode());
    }

    private static Goal goal(long id, int target, int current) {
        Goal goal = Goal.create(USER_ID, "여행 자금", target, current);
        ReflectionTestUtils.setField(goal, "id", id);
        return goal;
    }

    private static Suggestion adopted(long goalId, int expectedSaving) {
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.adopt(2, expectedSaving, goalId);
        return suggestion;
    }
}
