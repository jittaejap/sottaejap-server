package kr.sottaejap.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 PostgreSQL에 Flyway를 적용하고 전체 컨텍스트(JPA validate 포함)를 띄운다.
 * DB가 없는 로컬에서는 건너뛰고, CI는 RUN_DB_INTEGRATION_TESTS=true로 반드시 돌린다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class SottaejapServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
