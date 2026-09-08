package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.AnalysisResponse;
import kr.sottaejap.server.analysis.dto.InternalAnalysisResponse;
import kr.sottaejap.server.analysis.dto.SatisfactionMapResponse;

public interface AnalysisService {

    /** 만족도 지도 (API 14 · FR-07). AI를 부르지 않는다. */
    SatisfactionMapResponse satisfactionMap(long userId);

    /** 소비 분석 (API 22 · FR-11). '나만의 특징' 한 문장 때문에 AI를 부를 수 있다 (E-75). */
    AnalysisResponse analysis(long userId);

    /** AI `get_behavior_analysis` (05 §3). highlight가 없고, 그래서 AI를 부르지 않는다. */
    InternalAnalysisResponse internalAnalysis(long userId);
}
