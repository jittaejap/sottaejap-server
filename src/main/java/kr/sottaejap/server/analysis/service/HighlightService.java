package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.rules.aggregate.AnalysisSummary;

import java.time.YearMonth;

public interface HighlightService {

    /** 집계를 한 문장으로 (⑨ · FR-11-03). 실패하면 예외 대신 템플릿 문장을 돌려준다 — 분석은 200이어야 한다. */
    String highlight(long userId, YearMonth analysisYearMonth, AnalysisSummary summary);
}
