package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 제안 이유 문구 3종 (E-84 · FR-08-01). 이름 뒤에는 받침과 무관한 조사만 쓴다 (이슈 #20). */
class SuggestionReasonTemplateTest {

    @Test
    void PRIORITY는_월_합계를_쉼표_구분으로_인용하고_부담을_말한다() {
        assertEquals("심야 배달의 이번 달 지출이 96,000원이에요. 부담이 컸고 만족도도 낮았어요. 횟수를 줄여볼까요?",
                SuggestionReasonTemplate.reasonFor("심야 배달", cluster(Quadrant.PRIORITY, 0.096)));
    }

    @Test
    void MINOR는_부담이_크지_않다고_말한다() {
        assertEquals("부담이 크진 않지만 심야 배달의 만족도가 낮았어요. 조금만 줄여볼까요?",
                SuggestionReasonTemplate.reasonFor("심야 배달", cluster(Quadrant.MINOR, 0.02)));
    }

    /** 예산이 없어 좌표를 모르면 부담을 언급하지 않는다 (NFR-02). */
    @Test
    void 좌표를_모르면_부담을_언급하지_않는다() {
        String reason = SuggestionReasonTemplate.reasonFor("심야 배달", cluster(null, null));

        assertEquals("심야 배달의 만족도가 낮았어요. 몇 번만 줄여볼까요?", reason);
        assertFalse(reason.contains("부담"), reason);
    }

    /**
     * 묶음 이름은 AI가 짓는다 (⑤ · E-64) — 받침 유무 · 숫자 · 영문 · 이모지 어느 끝에서도 조사 `의`는 변하지 않는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"심야 배달", "카페", "배달 24", "배달 GS", "심야 배달 🍜"})
    void 이름_끝_문자와_무관하게_조사가_변하지_않는다(String behaviorName) {
        for (Quadrant quadrant : new Quadrant[] {Quadrant.PRIORITY, Quadrant.MINOR, null}) {
            String reason = SuggestionReasonTemplate.reasonFor(behaviorName, cluster(quadrant, null));

            assertTrue(reason.contains(behaviorName + "의 "), reason);
            assertFalse(reason.contains("은(는)"), reason);
        }
    }

    private static ClusterSnapshot cluster(Quadrant quadrant, Double burdenRatio) {
        return new ClusterSnapshot(12L, "배달|NIGHT|충동|혼자", null, null, 4, -0.42, 12_000, 96_000, 8,
                burdenRatio, EvaluationStatus.RESOLVED, quadrant, Verdict.ADJUST);
    }
}
