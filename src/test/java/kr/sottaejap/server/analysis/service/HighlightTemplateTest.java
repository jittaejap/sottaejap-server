package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** '나만의 특징' 폴백 문장 3종 (⑨ · E-75). */
class HighlightTemplateTest {

    @Test
    void 돌아본_소비가_없으면_회고를_권한다() {
        assertEquals(HighlightTemplate.NO_RETROSPECT, HighlightTemplate.highlightFor(summary(List.of())));
    }

    /**
     * 회고한 거래가 전부 기준월 밖이면 묶음은 서 있는데 월 합계가 0이라 byCategory가 통째로 빈다.
     * 이 사용자에게 "아직 돌아본 소비가 없어요"라고 말하지 않는다.
     */
    @Test
    void 묶음은_있는데_기준월_합계가_0이면_이번_달_회고를_권한다() {
        AnalysisSummary summary = new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 1, 0, null),
                        new VerdictSummary(Verdict.ADJUST, 2, 0, null)),
                new PendingSummary(0, 0, null),
                List.of());

        // E-89 — "바꿀 소비가 없다"(NO_ADJUST)도 "돌아본 소비가 없다"(NO_RETROSPECT)도 아니다.
        assertEquals(HighlightTemplate.NO_MONTH_ACTIVITY, HighlightTemplate.highlightFor(summary));
    }

    /**
     * 보류만 있는 사용자도 회고는 한 사용자다 (E-73 — 보류는 판정이 없을 뿐 유효 묶음이다).
     *
     * <p>여기서 막는 회귀는 {@code NO_RETROSPECT}("아직 돌아본 소비가 없어요")다 — 판정 2행이 모두 0이라고
     * 유효 묶음이 0인 것은 아니다.
     */
    @Test
    void 보류_묶음만_있어도_이번_달_회고를_권한다() {
        // 보류 금액은 카테고리 합계에 들어가므로(E-73) byCategory가 비려면 보류 합계도 0이어야 한다.
        AnalysisSummary summary = new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 0, 0, null),
                        new VerdictSummary(Verdict.ADJUST, 0, 0, null)),
                new PendingSummary(2, 0, null),
                List.of());

        assertEquals(HighlightTemplate.NO_MONTH_ACTIVITY, HighlightTemplate.highlightFor(summary));
    }

    @Test
    void 바꿔볼_소비가_없으면_그렇다고_말한다() {
        AnalysisSummary summary = summary(List.of(
                new CategorySummary("카페", TimeSlot.DAY, 28_000, 168_000, Verdict.SUSTAIN)));

        assertEquals(HighlightTemplate.NO_ADJUST, HighlightTemplate.highlightFor(summary));
    }

    @Test
    void 바꿔볼_소비가_있으면_가장_큰_카테고리와_금액을_말한다() {
        AnalysisSummary summary = summary(List.of(
                new CategorySummary("카페", TimeSlot.DAY, 28_000, 168_000, Verdict.SUSTAIN),
                new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 96_000, Verdict.ADJUST)));

        String highlight = HighlightTemplate.highlightFor(summary);

        assertTrue(highlight.contains("배달"), highlight);
        assertTrue(highlight.contains("96,000"), highlight);
    }

    @Test
    void 카테고리_합계를_바꿔볼_소비_금액이라고_말하지_않는다() {
        // 배달 21만원 = ADJUST 6만 + SUSTAIN 5만 + 보류 10만. 대표 판정만 ADJUST다.
        AnalysisSummary summary = summary(List.of(
                new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 210_000, Verdict.ADJUST)));

        String highlight = HighlightTemplate.highlightFor(summary);

        assertTrue(highlight.contains("210,000"), highlight);
        assertTrue(!highlight.contains("바꿔보기로 한 소비"), highlight);
        assertTrue(highlight.contains("카테고리"), highlight);
    }

    @Test
    void 집계에_없는_수치는_문장에_넣지_않는다() {
        AnalysisSummary summary = summary(List.of(
                new CategorySummary("배달", TimeSlot.NIGHT, 12_000, 96_000, Verdict.ADJUST)));

        String highlight = HighlightTemplate.highlightFor(summary);

        // 예산이 없어도 성립해야 하므로 비율은 쓰지 않는다.
        assertTrue(!highlight.contains("%"), highlight);
    }

    /**
     * 카테고리 한 행은 적어도 묶음 하나에서 나오므로 SUSTAIN 묶음 수를 행 수로 둔다 — 빈 목록이면 유효 묶음 0개,
     * 즉 정말 회고가 없는 사용자다. 문장 선택에 쓰는 것은 묶음 수와 byCategory뿐이라 금액은 0으로 둔다.
     */
    private static AnalysisSummary summary(List<CategorySummary> byCategory) {
        return new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, byCategory.size(), 0, null),
                        new VerdictSummary(Verdict.ADJUST, 0, 0, null)),
                new PendingSummary(0, 0, null),
                byCategory);
    }
}
