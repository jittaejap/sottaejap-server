package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.AnalysisSummary;
import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;

/**
 * '나만의 특징' 폴백 문장 3종 (⑨ · E-75). AI가 없거나(503) 빈 문장을 주거나 근거 밖 숫자를 쓰면 이 문장을 쓴다.
 *
 * <p>집계에 실제로 있는 수치만 쓴다 (NFR-02) — 예산이 없어도 성립하도록 비율 대신 금액을 인용한다.
 */
public final class HighlightTemplate {

    static final String NO_ADJUST = "이번 달은 바꿔보고 싶은 소비가 눈에 띄지 않았어요.";
    static final String NO_RETROSPECT = "아직 돌아본 소비가 없어요. 몇 건만 회고하면 나만의 특징이 보이기 시작해요.";

    private HighlightTemplate() {
    }

    /**
     * "돌아본 소비가 없다"는 <b>유효 묶음이 0개</b>일 때만 하는 말이다. {@code byCategory}가 비는 것은 그 조건이
     * 아니다 — 회고한 거래가 전부 기준월 밖이면 묶음은 서 있는데 월 합계가 0이라 카테고리가 통째로 빠진다
     * (E-73의 "합계 0인 카테고리는 뺀다"). 온보딩 표본 회고를 지난달에 하면 바로 밟는 경로다.
     *
     * <p>이때는 {@link #NO_ADJUST}로 보낸다. 문장을 새로 두는 것은 E-75의 "템플릿 3종"을 4종으로 바꾸는
     * 결정이라 이 판에서 하지 않는다.
     */
    public static String highlightFor(AnalysisSummary summary) {
        if (effectiveClusterCount(summary) == 0) {
            return NO_RETROSPECT;
        }
        return summary.byCategory().stream()
                .filter(category -> category.verdict() == Verdict.ADJUST)
                .findFirst()
                .map(HighlightTemplate::adjustSentence)
                .orElse(NO_ADJUST);
    }

    /** 유효 묶음 수 (E-72) — 판정 2행의 묶음 수와 보류 묶음 수를 더한 값이다. */
    private static int effectiveClusterCount(AnalysisSummary summary) {
        return summary.byVerdict().stream().mapToInt(VerdictSummary::clusterCount).sum()
                + summary.pending().clusterCount();
    }

    /**
     * byCategory는 월 합계 내림차순이므로 첫 ADJUST 행이 곧 판정이 '바꿔볼 소비'인 가장 큰 카테고리다 (E-73).
     *
     * <p>금액은 <b>카테고리 전체 합계</b>다 — 같은 카테고리의 SUSTAIN·보류 묶음 금액도 들어 있다.
     * 그래서 "이 금액이 바꿔볼 소비"라고 말하지 않는다. 판정은 카테고리의 대표 판정이고, 금액은 카테고리
     * 합계다 — 둘을 한 문장에 넣되 각각이 무엇인지 어긋나지 않게 쓴다 (NFR-02).
     */
    private static String adjustSentence(CategorySummary category) {
        return "이번 달 %s에 %,d원을 썼고, 이 카테고리는 '바꿔볼 소비'로 판정됐어요."
                .formatted(category.category(), category.monthlyTotalAmount());
    }
}
