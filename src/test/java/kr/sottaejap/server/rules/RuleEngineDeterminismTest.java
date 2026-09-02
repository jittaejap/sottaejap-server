package kr.sottaejap.server.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR-01 증명 (E-29) — 같은 입력에 같은 출력.
 *
 * <p>규칙 엔진 코드가 생기기 전에는 "비결정 요소를 참조하지 않는다"는 구조적 조건만 검사한다.
 * 규칙이 하나씩 붙을 때마다 그 규칙의 입력 → 출력 고정 케이스를 이 클래스에 추가한다.
 */
class RuleEngineDeterminismTest {

    private static final Path RULES_SOURCE_ROOT = Path.of("src/main/java/kr/sottaejap/server/rules");

    /** HTTP·LLM·난수·현재시각 (07 §1 규칙 엔진 행). */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "java.util.Random",
            "ThreadLocalRandom",
            "Math.random",
            "SecureRandom",
            "UUID.randomUUID",
            "LocalDateTime.now",
            "LocalDate.now",
            "Instant.now",
            "OffsetDateTime.now",
            "ZonedDateTime.now",
            "System.currentTimeMillis",
            "Clock.system",
            "RestClient",
            "RestTemplate",
            "WebClient",
            "HttpClient",
            "kr.sottaejap.server.ai"
    );

    @Test
    void rulesPackage_doesNotReferenceNonDeterministicSources() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RULES_SOURCE_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, violations));
        }
        assertTrue(violations.isEmpty(), () -> "규칙 엔진은 결정론이어야 합니다:\n" + String.join("\n", violations));
    }

    @Test
    void ruleParams_sameInput_sameValue() {
        RuleParams first = new RuleParams(4.0, 3, 2, 0.05, 0.0);
        RuleParams second = new RuleParams(4.0, 3, 2, 0.05, 0.0);
        assertEquals(first, second);
    }

    private static void collectViolations(Path path, List<String> violations) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String token : FORBIDDEN_TOKENS) {
                if (source.contains(token)) {
                    violations.add(path + " → " + token);
                }
            }
        } catch (IOException exception) {
            violations.add(path + " → 읽기 실패: " + exception.getMessage());
        }
    }
}
