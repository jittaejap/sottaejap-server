package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.analysis.dto.CtaView;
import kr.sottaejap.server.common.enums.CtaType;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;

/**
 * 처방 문구 5종과 CTA 3종 (FR-07-04 · FR-07-07 · E-76). AI를 부르지 않는다 — 판정에서 문구가 바로 나오므로
 * `ai` 컨테이너가 내려가도 지도는 그대로 뜬다 (E-38).
 *
 * <p>예산이 없으면 가로축이 없어 quadrant가 null이다 (E-61). 그때는 세로축 부호만 남은 verdict로 문구를
 * 고르고, CTA는 걸지 않는다 — 부담을 모르는 상태에서 "예산 확보"·"조정"을 권할 근거가 없다.
 */
public final class PrescriptionTemplate {

    static final String PROTECT = "Great! 이건 지킬 가치가 있어요. 예산을 미리 확보해둘까요?";
    static final String KEEP = "Awesome!! 부담 없이 만족스러운 소비예요. 이대로 두셔도 좋아요.";
    static final String MINOR = "Umm… 만족은 낮았지만 부담은 크지 않아요. 급하게 바꾸지 않아도 돼요.";
    static final String PRIORITY = "Hmm! 여기부터 볼까요? 부담은 큰데 만족은 낮았던 소비예요.";
    static final String PENDING = "아직 판단하기엔 이르네요. 조금 더 지켜볼게요.";

    private PrescriptionTemplate() {
    }

    public static String prescriptionFor(ClusterSnapshot cluster) {
        if (cluster.evaluationStatus() == EvaluationStatus.PENDING) {
            return PENDING;
        }
        if (cluster.quadrant() == null) {
            return cluster.verdict() == Verdict.SUSTAIN ? KEEP : MINOR;
        }
        return switch (cluster.quadrant()) {
            case PROTECT -> PROTECT;
            case KEEP -> KEEP;
            case MINOR -> MINOR;
            case PRIORITY -> PRIORITY;
        };
    }

    public static CtaView ctaFor(ClusterSnapshot cluster) {
        if (cluster.evaluationStatus() == EvaluationStatus.PENDING || cluster.quadrant() == null) {
            return null;
        }
        return switch (cluster.quadrant()) {
            case PROTECT -> new CtaView(CtaType.RESERVE_BUDGET, "예산 확보하기");
            case MINOR, PRIORITY -> new CtaView(CtaType.ADJUST, "조정하기");
            case KEEP -> null;
        };
    }
}
