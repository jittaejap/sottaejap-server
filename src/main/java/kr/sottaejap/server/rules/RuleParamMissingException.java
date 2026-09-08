package kr.sottaejap.server.rules;

/**
 * `rules.*` 값이 비어 규칙이 계산을 거부할 때. 기본값으로 대체하지 않는다 (server AGENTS · E-18).
 * 500으로 드러나야 운영자가 yml을 채운다.
 */
public class RuleParamMissingException extends IllegalStateException {

    public RuleParamMissingException(String key) {
        super("규칙 파라미터가 비어 있어 계산할 수 없다: " + key + " (07 §7 · E-57)");
    }
}
