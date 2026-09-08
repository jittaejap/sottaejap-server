package kr.sottaejap.server.rules.aggregate;

import kr.sottaejap.server.common.enums.Verdict;

/**
 * 판정별 집계 한 행 (E-73 · 05 §2 `byVerdict`). SUSTAIN · ADJUST 두 행이 항상 나온다 — 해당 묶음이
 * 하나도 없으면 0으로 채운 행이다. AI가 "없다"와 "못 받았다"를 구분하지 못하기 때문이다.
 *
 * @param share 월 합계 ÷ 월 예산. 예산이 없으면 null이며 반올림하지 않는다
 */
public record VerdictSummary(Verdict verdict, int clusterCount, int monthlyTotalAmount, Double share) {
}
