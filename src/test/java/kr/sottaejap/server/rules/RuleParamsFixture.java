package kr.sottaejap.server.rules;

import java.util.List;

/** E-57 잠정값과 같은 테스트용 RuleParams. 잠정값이 바뀌면 여기 한 곳만 고친다. */
public final class RuleParamsFixture {

    private RuleParamsFixture() {
    }

    public static RuleParams sample() {
        return new RuleParams(3.0, 3, 3, 0.1, 0.0, 3,
                new RuleParams.Sensitivity(3.0, 2.0, 1.5),
                new RuleParams.Candidate(90, 5, 0.1, 2),
                new RuleParams.Cluster(List.of("식사", "식비", "배달", "카페")));
    }
}
