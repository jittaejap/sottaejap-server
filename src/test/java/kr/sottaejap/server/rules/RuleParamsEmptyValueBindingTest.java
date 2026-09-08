package kr.sottaejap.server.rules;

import kr.sottaejap.server.rules.cluster.ClusterKeyRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * `.env`에 `RULES_*=`처럼 <b>키를 두고 값만 비웠을 때</b> 무엇이 바인딩되는지 고정한다.
 *
 * <p>{@code ${RULES_X:잠정값}}은 키가 <b>아예 없을 때만</b> 잠정값을 준다. 키가 있으면 빈 문자열이
 * 그대로 풀려서 숫자는 null, 리스트는 빈 리스트가 된다 — 잠정값이 아니다. 이 테스트가 그 사실을 박아 두고,
 * 두 갈래 모두 계산 전에 {@link RuleParamMissingException}으로 드러나는지 확인한다.
 * 잠정값이 실제로 붙는지는 {@link RuleParamsBindingTest}가 컨텍스트로 본다.
 */
class RuleParamsEmptyValueBindingTest {

    @Test
    void 값을_비우면_잠정값이_아니라_null과_빈_리스트가_들어온다() {
        RuleParams params = bind(Map.of(
                "rules.shrinkage-k", "",
                "rules.rollup-min-count", "",
                "rules.cluster.meal-categories", ""));

        assertNull(params.shrinkageK());
        assertNull(params.rollupMinCount());
        assertEquals(List.of(), params.cluster().mealCategories());
    }

    @Test
    void 값을_비운_두_갈래_모두_계산_전에_예외로_드러난다() {
        RuleParams params = bind(Map.of(
                "rules.shrinkage-k", "",
                "rules.cluster.meal-categories", ""));

        assertThrows(RuleParamMissingException.class,
                () -> RuleParams.require(params.shrinkageK(), "rules.shrinkage-k"));
        assertThrows(RuleParamMissingException.class,
                () -> ClusterKeyRule.includesTimeSlot("배달", params));
    }

    private static RuleParams bind(Map<String, String> properties) {
        Map<String, Object> source = new HashMap<>(properties);
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("rules", RuleParams.class)
                .orElseThrow(() -> new IllegalStateException("rules 바인딩 실패"));
    }
}
