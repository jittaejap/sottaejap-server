package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 제안 이유 문구 3종 (E-84 · FR-08-01). AI를 부르지 않는다 — 판정에서 바로 나오므로 `ai` 컨테이너가
 * 내려가도 제안 목록이 그대로 뜬다 (E-38 · `PrescriptionTemplate` 선례).
 *
 * <p>집계에 있는 수치만 쓴다 (NFR-02). 예산이 없어 좌표를 모르면 <b>부담을 언급하지 않는다</b> —
 * 모르는 것을 "크지 않다"고 말할 수는 없다.
 */
public final class SuggestionReasonTemplate {

    private SuggestionReasonTemplate() {
    }

    public static String reasonFor(String behaviorName, ClusterSnapshot cluster) {
        if (cluster.quadrant() == Quadrant.PRIORITY) {
            return "%s은(는) 이번 달 %,d원으로 부담이 컸고 만족도도 낮았어요. 횟수를 줄여볼까요?"
                    .formatted(behaviorName, cluster.monthlyTotalAmount());
        }
        if (cluster.quadrant() == Quadrant.MINOR) {
            return "%s은(는) 부담이 크진 않지만 만족도가 낮았어요. 조금만 줄여볼까요?".formatted(behaviorName);
        }
        return "%s은(는) 만족도가 낮았어요. 몇 번만 줄여볼까요?".formatted(behaviorName);
    }
}
