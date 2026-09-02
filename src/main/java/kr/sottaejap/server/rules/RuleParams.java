package kr.sottaejap.server.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml `rules.*` 바인딩 (07 §7). 9/7 튜닝은 값 주입만으로 끝나야 하므로 코드에 숫자를 쓰지 않는다.
 * 미결값은 null이며, 각 규칙은 null이면 계산을 거부해야 한다 — 기본값으로 조용히 대체하지 않는다.
 *
 * @param shrinkageK       축소 추정 강도 k (액션시트 #15, 잠정 3~5)
 * @param rollupMinCount   롤업 기준 건수 (#16)
 * @param pendingMinCount  보류 임계값 (#17) — rollupMinCount 이하
 * @param axisXBoundary    가로축 경계 Bx = 월 합계 ÷ 월 예산 (#18)
 * @param axisYBoundary    세로축 경계 By (#18) — 0 또는 사용자 평균
 */
@ConfigurationProperties("rules")
public record RuleParams(
        Double shrinkageK,
        Integer rollupMinCount,
        Integer pendingMinCount,
        Double axisXBoundary,
        Double axisYBoundary
) {
}
