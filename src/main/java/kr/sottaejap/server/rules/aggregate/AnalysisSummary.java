package kr.sottaejap.server.rules.aggregate;

import java.util.List;

/** 소비 분석 집계 결과 (⑧ · E-73). 문장은 이 안에 없다 — 수치를 문장으로 바꾸는 일은 표현 계층 몫이다. */
public record AnalysisSummary(List<VerdictSummary> byVerdict, PendingSummary pending, List<CategorySummary> byCategory) {
}
