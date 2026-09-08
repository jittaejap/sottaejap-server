package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.rules.aggregate.CategorySummary;
import kr.sottaejap.server.rules.aggregate.PendingSummary;
import kr.sottaejap.server.rules.aggregate.VerdictSummary;

import java.util.List;

/**
 * 소비 분석 (API 22 · FR-11). 집계 record를 그대로 싣는다 — 필드 이름이 05 §2와 이미 같아서
 * 뷰를 한 겹 더 두면 두 곳이 어긋날 자리만 생긴다.
 *
 * @param highlight 집계를 AI가 재구성한 한 문장 (⑨ · E-75). AI가 없거나 근거 밖 숫자를 쓰면 Spring 템플릿이다
 */
public record AnalysisResponse(
        String analysisYearMonth,
        List<VerdictSummary> byVerdict,
        PendingSummary pending,
        List<CategorySummary> byCategory,
        String highlight
) {
}
