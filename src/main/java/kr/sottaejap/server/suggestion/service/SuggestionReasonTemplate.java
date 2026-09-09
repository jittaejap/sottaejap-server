package kr.sottaejap.server.suggestion.service;

import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 제안 이유 문구 3종 (E-84 · FR-08-01). AI를 부르지 않는다 — 판정에서 바로 나오므로 `ai` 컨테이너가
 * 내려가도 제안 목록이 그대로 뜬다 (E-38 · `PrescriptionTemplate` 선례).
 *
 * <p>집계에 있는 수치만 쓴다 (NFR-02). 예산이 없어 좌표를 모르면 <b>부담을 언급하지 않는다</b> —
 * 모르는 것을 "크지 않다"고 말할 수는 없다.
 *
 * <p><b>이름 뒤에는 받침과 무관한 조사(`의` · `에`)만 쓴다</b> (이슈 #20 · PR #44 리뷰). 이름은 AI가 짓기 때문에
 * 숫자 · 영문 · 이모지로 끝날 수 있어 종성 판별로도 `은(는)` 폴백이 남는다. `HighlightTemplate`의 `"이번 달 %s에 …"`와
 * 같은 방식이다. PRIORITY만 "지출"을 주어로 세워 여는데, 그 highlight 문장(카테고리 전체 합계)과 같은 문형으로
 * 나란히 놓이면 금액을 두 번 쓴 것으로 읽히기 때문이다 — 여기 금액은 ADJUST 묶음 하나의 월 합계다.
 */
public final class SuggestionReasonTemplate {

    private SuggestionReasonTemplate() {
    }

    public static String reasonFor(String behaviorName, ClusterSnapshot cluster) {
        if (cluster.quadrant() == Quadrant.PRIORITY) {
            return "%s의 이번 달 지출이 %,d원이에요. 부담이 컸고 만족도도 낮았어요. 횟수를 줄여볼까요?"
                    .formatted(behaviorName, cluster.monthlyTotalAmount());
        }
        if (cluster.quadrant() == Quadrant.MINOR) {
            return "부담이 크진 않지만 %s의 만족도가 낮았어요. 조금만 줄여볼까요?".formatted(behaviorName);
        }
        return "%s의 만족도가 낮았어요. 몇 번만 줄여볼까요?".formatted(behaviorName);
    }
}
