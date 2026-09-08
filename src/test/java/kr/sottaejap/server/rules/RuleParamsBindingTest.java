package kr.sottaejap.server.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * application.yml `rules.*` 잠정값(E-57)이 중첩 record까지 바인딩되는지 실제 컨텍스트로 확인한다.
 * 환경변수 RULES_*가 비어 있는 상태를 전제한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class RuleParamsBindingTest {

    @Autowired
    private RuleParams params;

    @Test
    void 잠정값이_중첩_record까지_바인딩된다() {
        assertEquals(3.0, params.shrinkageK());
        assertEquals(3, params.rollupMinCount());
        assertEquals(3, params.pendingMinCount());
        assertEquals(0.1, params.axisXBoundary());
        assertEquals(0.0, params.axisYBoundary());
        assertEquals(3, params.chatWindowDays());
        assertEquals(2.0, params.sensitivity().standard());
        assertEquals(90, params.candidate().outlierBaselineDays());
        assertEquals(0.1, params.candidate().bigAmountBudgetRatio());
        assertEquals(List.of("식사", "식비", "음식점", "외식", "배달", "한식", "중식", "일식", "양식", "분식", "패스트푸드", "카페"),
                params.cluster().mealCategories());
    }
}
