package kr.sottaejap.server.analysis.service;

import kr.sottaejap.server.common.enums.CtaType;
import kr.sottaejap.server.common.enums.EvaluationStatus;
import kr.sottaejap.server.common.enums.Quadrant;
import kr.sottaejap.server.common.enums.Verdict;
import kr.sottaejap.server.rules.aggregate.ClusterSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 처방 5종 · CTA 3종 (FR-07-04 · FR-07-07 · E-76). */
class PrescriptionTemplateTest {

    @Test
    void 좌표마다_다른_처방을_준다() {
        assertEquals(PrescriptionTemplate.PROTECT, PrescriptionTemplate.prescriptionFor(at(Quadrant.PROTECT)));
        assertEquals(PrescriptionTemplate.KEEP, PrescriptionTemplate.prescriptionFor(at(Quadrant.KEEP)));
        assertEquals(PrescriptionTemplate.MINOR, PrescriptionTemplate.prescriptionFor(at(Quadrant.MINOR)));
        assertEquals(PrescriptionTemplate.PRIORITY, PrescriptionTemplate.prescriptionFor(at(Quadrant.PRIORITY)));
    }

    @Test
    void 보류_묶음은_판단을_미루는_문구다() {
        assertEquals(PrescriptionTemplate.PENDING, PrescriptionTemplate.prescriptionFor(pending()));
        assertNull(PrescriptionTemplate.ctaFor(pending()));
    }

    @Test
    void CTA는_예산_확보와_조정_둘뿐이고_KEEP에는_없다() {
        assertEquals(new kr.sottaejap.server.analysis.dto.CtaView(CtaType.RESERVE_BUDGET, "예산 확보하기"),
                PrescriptionTemplate.ctaFor(at(Quadrant.PROTECT)));
        assertEquals(CtaType.ADJUST, PrescriptionTemplate.ctaFor(at(Quadrant.MINOR)).type());
        assertEquals(CtaType.ADJUST, PrescriptionTemplate.ctaFor(at(Quadrant.PRIORITY)).type());
        assertNull(PrescriptionTemplate.ctaFor(at(Quadrant.KEEP)));
    }

    @Test
    void 예산이_없어_좌표를_모르면_판정으로_고르고_CTA는_걸지_않는다() {
        ClusterSnapshot sustain = snapshot(EvaluationStatus.RESOLVED, null, Verdict.SUSTAIN);
        ClusterSnapshot adjust = snapshot(EvaluationStatus.RESOLVED, null, Verdict.ADJUST);

        assertEquals(PrescriptionTemplate.KEEP, PrescriptionTemplate.prescriptionFor(sustain));
        assertEquals(PrescriptionTemplate.MINOR, PrescriptionTemplate.prescriptionFor(adjust));
        assertNull(PrescriptionTemplate.ctaFor(sustain));
        assertNull(PrescriptionTemplate.ctaFor(adjust));
    }

    private static ClusterSnapshot at(Quadrant quadrant) {
        Verdict verdict = quadrant == Quadrant.PROTECT || quadrant == Quadrant.KEEP ? Verdict.SUSTAIN : Verdict.ADJUST;
        return snapshot(EvaluationStatus.RESOLVED, quadrant, verdict);
    }

    private static ClusterSnapshot pending() {
        return snapshot(EvaluationStatus.PENDING, null, null);
    }

    private static ClusterSnapshot snapshot(EvaluationStatus status, Quadrant quadrant, Verdict verdict) {
        return new ClusterSnapshot(1L, "배달|NIGHT||", "심야 배달", null, 3, -0.4, 12000, 96000, 8, 0.096,
                status, quadrant, verdict);
    }
}
