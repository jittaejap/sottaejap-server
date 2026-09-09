package kr.sottaejap.server.suggestion.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 이유 문장의 수명 — 인용한 숫자가 바뀌면 버린다 (05 §3 ACTION_PLAN). */
class SuggestionTest {

    @Test
    void 같은_값으로_다시_계산하면_이유를_지킨다() {
        // 동기화는 값이 그대로여도 매번 refresh를 부른다. 무조건 비우면 모든 재계산이 문장을 지운다.
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.explain("심야 배달을 두 번만 줄여도 24,000원이 남아요.");

        suggestion.refresh(8, 96_000);

        assertEquals("심야 배달을 두 번만 줄여도 24,000원이 남아요.", suggestion.getReason());
    }

    @Test
    void 건수가_바뀌면_이유를_버린다() {
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.explain("여덟 번 중 두 번만 줄여도 24,000원이 남아요.");

        suggestion.refresh(9, 96_000);

        assertNull(suggestion.getReason());
    }

    @Test
    void 절감액이_바뀌면_이유를_버린다() {
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.explain("두 번만 줄여도 24,000원이 남아요.");

        suggestion.refresh(8, 108_000);

        assertNull(suggestion.getReason());
    }

    @Test
    void 채택으로_횟수를_고쳐도_이유를_버린다() {
        // 카드에는 사용자가 고른 2회·24,000원이 뜨는데 문장이 8회·96,000원을 말하면 서로 어긋난다.
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.explain("여덟 번 중 몇 번만 줄여도 96,000원이 남아요.");

        suggestion.adopt(2, 24_000, null);

        assertNull(suggestion.getReason());
    }

    @Test
    void 같은_횟수로_다시_채택하면_이유를_지킨다() {
        Suggestion suggestion = Suggestion.propose(1L, 8, 96_000);
        suggestion.adopt(2, 24_000, null);
        suggestion.explain("두 번만 줄여도 24,000원이 남아요.");

        // 목표만 바꾸는 재채택 (FR-08-05) — 숫자가 그대로면 문장도 그대로다.
        suggestion.adopt(2, 24_000, 5L);

        assertEquals("두 번만 줄여도 24,000원이 남아요.", suggestion.getReason());
    }
}
