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

    private static AnalysisSummary summary(List<CategorySummary> byCategory) {
        return new AnalysisSummary(
                List.of(new VerdictSummary(Verdict.SUSTAIN, 0, 0, null),
                        new VerdictSummary(Verdict.ADJUST, 0, 0, null)),
                new PendingSummary(0, 0, null),
                byCategory);
    }
}
