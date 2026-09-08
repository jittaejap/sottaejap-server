package kr.sottaejap.server.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yml `rules.*` 바인딩 (07 §7). 9/7 튜닝은 값 주입만으로 끝나야 하므로 코드에 숫자를 쓰지 않는다.
 *
 * <p>v2.2(E-57)부터 잠정값이 yml 기본값으로 들어 있고 `RULES_*` 환경변수로 덮어쓴다. 값이 null이면 각 규칙은
 * {@link #require(Object, String)}로 계산을 거부한다 — 기본값으로 조용히 대체하지 않는다.
 *
 * @param shrinkageK      축소 추정 강도 k (액션시트 #15)
 * @param rollupMinCount  롤업 기준 건수 (#16) — 리프 회고 수가 이보다 작으면 상위 묶음에 붙는다 (E-59)
 * @param pendingMinCount 보류 임계값 (#17) — rollupMinCount 이하여야 한다
 * @param axisXBoundary   가로축 경계 Bx = 월 합계 ÷ 월 예산 (#18)
 * @param axisYBoundary   세로축 경계 By (#18) — 0 또는 사용자 평균
 * @param chatWindowDays  채팅 회고 진입 노출 창, 오늘 포함 (E-48)
 * @param sensitivity     E-46 프리셋 3종 — 이상치 중앙값 배수
 * @param candidate       후보 선별 ⓪ 수치 (#20 · E-62)
 * @param cluster         묶음 키 규칙 (B-10 · E-58)
 */
@ConfigurationProperties("rules")
public record RuleParams(
        Double shrinkageK,
        Integer rollupMinCount,
        Integer pendingMinCount,
        Double axisXBoundary,
        Double axisYBoundary,
        Integer chatWindowDays,
        Sensitivity sensitivity,
        Candidate candidate,
        Cluster cluster
) {

    public RuleParams {
        if (rollupMinCount != null && pendingMinCount != null && pendingMinCount > rollupMinCount) {
            throw new IllegalStateException("rules.pending-min-count(" + pendingMinCount
                    + ")는 rules.rollup-min-count(" + rollupMinCount + ") 이하여야 한다 (액션시트 #17)");
        }
    }

    /** 이상치 배수 프리셋 (E-46). 사용자 설정 `outlierThreshold`가 없을 때 standard를 쓴다 (E-62). */
    public record Sensitivity(Double conservative, Double standard, Double sensitive) {
    }

    /**
     * 후보 선별 수치 (E-62, 잠정).
     *
     * @param outlierBaselineDays  같은 (카테고리, 시간대) 기준선으로 보는 최근 일수
     * @param outlierMinSamples    기준선 표본이 이보다 적으면 카테고리 전체로 롤업, 그래도 부족하면 미적용
     * @param bigAmountBudgetRatio THRESHOLD_EXCEEDED = amount ≥ monthlyBudget × 이 값
     * @param repeatedLowMinCount  같은 상위 키 아래 LOW 회고가 이 수 이상이면 REPEATED_LOW_SATISFACTION
     */
    public record Candidate(
            Integer outlierBaselineDays,
            Integer outlierMinSamples,
            Double bigAmountBudgetRatio,
            Integer repeatedLowMinCount
    ) {
    }

    /** 묶음 키에 시간대를 포함하는 카테고리 목록 (B-10). `기타`(미분류)는 목록과 무관하게 포함한다 (E-58). */
    public record Cluster(List<String> mealCategories) {
    }

    /** null이면 계산을 거부한다. key는 `rules.shrinkage-k`처럼 yml 키 그대로 적어 메시지에서 바로 찾게 한다. */
    public static <T> T require(T value, String key) {
        if (value == null) {
            throw new RuleParamMissingException(key);
        }
        return value;
    }
}
