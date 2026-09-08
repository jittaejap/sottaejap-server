package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.rules.RuleParamMissingException;
import kr.sottaejap.server.rules.RuleParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 묶음 키 조합 고정 케이스 (② · B-10 · E-58 · E-59). */
class ClusterKeyRuleTest {

    /** 목록 마지막 항목에 앞뒤 공백을 넣어 trim 비교를 함께 고정한다. */
    private static final RuleParams PARAMS = params(List.of("식사", "배달", "  카페  "));

    @Test
    void 식사_카테고리는_시간대를_포함한_리프키를_만든다() {
        assertEquals("식사|NIGHT|충동|혼자",
                ClusterKeyRule.leafKey("식사", TimeSlot.NIGHT, "충동", "혼자", PARAMS));
    }

    @Test
    void 식사가_아닌_카테고리는_시간대_자리를_비운다() {
        assertEquals("교통||필수품|혼자",
                ClusterKeyRule.leafKey("교통", TimeSlot.NIGHT, "필수품", "혼자", PARAMS));
    }

    @Test
    void 미분류_기타는_목록과_무관하게_시간대를_포함한다() {
        assertEquals("기타|DAY|식사|친구",
                ClusterKeyRule.leafKey("기타", TimeSlot.DAY, "식사", "친구", PARAMS));
    }

    @Test
    void 카테고리가_null이면_기타로_취급한다() {
        assertEquals("기타|DAY|식사|친구",
                ClusterKeyRule.leafKey(null, TimeSlot.DAY, "식사", "친구", PARAMS));
    }

    @Test
    void 목적과_동행인이_null이면_빈_자리로_두고_상위_키와_같은_모양이_된다() {
        String leafKey = ClusterKeyRule.leafKey("식사", TimeSlot.NIGHT, null, null, PARAMS);

        assertEquals("식사|NIGHT||", leafKey);
        assertEquals(ClusterKeyRule.parentKey("식사", TimeSlot.NIGHT, PARAMS), leafKey);
        assertTrue(ClusterKeyRule.isParentKey(leafKey));
    }

    @Test
    void 시간대가_null이면_식사_카테고리여도_빈_자리로_둔다() {
        assertEquals("식사||충동|혼자",
                ClusterKeyRule.leafKey("식사", null, "충동", "혼자", PARAMS));
    }

    @Test
    void 상위_키는_목적과_동행인을_비운다() {
        assertEquals("식사|NIGHT||", ClusterKeyRule.parentKey("식사", TimeSlot.NIGHT, PARAMS));
        assertEquals("교통|||", ClusterKeyRule.parentKey("교통", TimeSlot.NIGHT, PARAMS));
    }

    @Test
    void 리프_키에서_상위_키를_뽑는다() {
        assertEquals(ClusterKeyRule.parentKey("식사", TimeSlot.NIGHT, PARAMS),
                ClusterKeyRule.parentKeyOf("식사|NIGHT|충동|혼자"));
        assertEquals(ClusterKeyRule.parentKey("교통", TimeSlot.NIGHT, PARAMS),
                ClusterKeyRule.parentKeyOf("교통||필수품|혼자"));
    }

    @Test
    void 상위_키를_다시_뽑아도_그대로다() {
        assertEquals("식사|NIGHT||", ClusterKeyRule.parentKeyOf("식사|NIGHT||"));
    }

    @Test
    void 네_자리가_아닌_키는_상위_키를_뽑지_않는다() {
        assertThrows(IllegalArgumentException.class, () -> ClusterKeyRule.parentKeyOf("식사|NIGHT"));
    }

    @Test
    void 시간대_포함_여부는_목록과_기타로_정한다() {
        assertTrue(ClusterKeyRule.includesTimeSlot("식사", PARAMS));
        assertFalse(ClusterKeyRule.includesTimeSlot("교통", PARAMS));
        assertTrue(ClusterKeyRule.includesTimeSlot(ClusterKeyRule.UNCATEGORIZED, PARAMS));
        assertTrue(ClusterKeyRule.includesTimeSlot(null, PARAMS));
    }

    @Test
    void 목록_항목의_앞뒤_공백은_떼고_비교한다() {
        assertTrue(ClusterKeyRule.includesTimeSlot("카페", PARAMS));
        assertTrue(ClusterKeyRule.includesTimeSlot("  카페  ", PARAMS));
    }

    @Test
    void 상위_키_판정은_뒤_두_자리가_비었는지로_한다() {
        assertTrue(ClusterKeyRule.isParentKey("식사|NIGHT||"));
        assertFalse(ClusterKeyRule.isParentKey("식사|NIGHT|충동|혼자"));
        assertFalse(ClusterKeyRule.isParentKey("식사|NIGHT|충동|"));
    }

    @Test
    void 식사_카테고리_목록이_없으면_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> ClusterKeyRule.includesTimeSlot("식사", params(null)));
        assertTrue(exception.getMessage().contains("rules.cluster.meal-categories"));
    }

    /** `RULES_CLUSTER_MEAL_CATEGORIES=`로 값만 비우면 null이 아니라 빈 리스트가 들어온다 — 여기서 막는다 (E-58). */
    @Test
    void 식사_카테고리_목록이_비어_있어도_계산을_거부한다() {
        RuleParamMissingException exception = assertThrows(RuleParamMissingException.class,
                () -> ClusterKeyRule.includesTimeSlot("식사", params(List.of())));
        assertTrue(exception.getMessage().contains("rules.cluster.meal-categories"));

        assertThrows(RuleParamMissingException.class,
                () -> ClusterKeyRule.leafKey("배달", TimeSlot.NIGHT, "충동", "혼자", params(List.of())));
    }

    @Test
    void 묶음_파라미터_자체가_비어도_계산을_거부한다() {
        RuleParams noCluster = new RuleParams(3.0, 3, 3, 0.1, 0.0, 3, null, null, null);

        assertThrows(RuleParamMissingException.class,
                () -> ClusterKeyRule.leafKey("식사", TimeSlot.NIGHT, "충동", "혼자", noCluster));
    }

    private static RuleParams params(List<String> mealCategories) {
        return new RuleParams(3.0, 3, 3, 0.1, 0.0, 3,
                new RuleParams.Sensitivity(3.0, 2.0, 1.5),
                new RuleParams.Candidate(90, 5, 0.1, 2),
                new RuleParams.Cluster(mealCategories));
    }
}
