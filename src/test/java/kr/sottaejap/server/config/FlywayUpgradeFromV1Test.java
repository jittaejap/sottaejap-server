package kr.sottaejap.server.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 이미 데이터가 들어 있는 V1 DB를 최신 버전으로 올린다 (E-50 · 06 R13).
 *
 * <p>깨끗한 DB만 쓰는 CI는 이 경로를 보지 못한다. V2가 옛 CHECK를 살려둔 채 DAY·EVENING을 쓰면
 * SQLSTATE 23514로 기동이 실패했는데, 그때도 다른 테스트는 전부 통과했다. 그래서 여기서는
 * V1까지만 적용한 별도 스키마에 옛 값(AFTERNOON · 11시대 MORNING)을 넣고 나머지를 올린다.
 *
 * <p>운영 스키마를 건드리지 않도록 전용 스키마를 만들고 테스트가 끝나면 통째로 지운다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class FlywayUpgradeFromV1Test {

    private static final String SCHEMA = "flyway_upgrade_test";

    /** V1 스키마 시절의 거래. 가맹점명이 곧 기대값의 열쇠다. */
    private static final String INSERT_V1_TRANSACTIONS = """
            INSERT INTO %s.transactions
                (user_id, occurred_at, merchant, amount, category, card_issuer, time_slot, import_hash)
            VALUES
                (1, TIMESTAMPTZ '2026-08-20 03:00:00+09', '새벽편의점', 4000, '기타', 'KB', 'NIGHT',     'hash-0300'),
                (1, TIMESTAMPTZ '2026-08-20 08:00:00+09', '아침카페',   4500, '기타', 'KB', 'MORNING',   'hash-0800'),
                (1, TIMESTAMPTZ '2026-08-20 11:30:00+09', '늦은아침',   9000, '기타', 'KB', 'MORNING',   'hash-1130'),
                (1, TIMESTAMPTZ '2026-08-20 13:00:00+09', '점심식당',  12000, '기타', 'KB', 'AFTERNOON', 'hash-1300'),
                (1, TIMESTAMPTZ '2026-08-20 19:00:00+09', '저녁식당',  25000, '기타', 'KB', 'AFTERNOON', 'hash-1900'),
                (1, TIMESTAMPTZ '2026-08-20 23:10:00+09', '야식배달',  18000, '기타', 'KB', 'NIGHT',     'hash-2310')
            """.formatted(SCHEMA);

    @BeforeEach
    void createEmptySchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
    }

    @AfterEach
    void dropSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void V1_데이터가_있어도_최신까지_올라가고_시간대가_4구간으로_재분류된다() throws SQLException {
        migrate(MigrationVersion.fromVersion("1"));
        execute(INSERT_V1_TRANSACTIONS);

        migrate(MigrationVersion.LATEST);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("새벽편의점", "NIGHT");
        expected.put("아침카페", "MORNING");
        // 11:00~11:59는 옛 경계에서 MORNING이었다. AFTERNOON만 갱신하면 여기가 그대로 남는다.
        expected.put("늦은아침", "DAY");
        expected.put("점심식당", "DAY");
        expected.put("저녁식당", "EVENING");
        expected.put("야식배달", "NIGHT");

        assertEquals(expected, timeSlotByMerchant());
    }

    private static void migrate(MigrationVersion target) {
        Flyway.configure()
                .dataSource(env("DB_URL"), env("DB_USER"), env("DB_PASSWORD"))
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static Map<String, String> timeSlotByMerchant() throws SQLException {
        Map<String, String> found = new LinkedHashMap<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT merchant, time_slot FROM " + SCHEMA + ".transactions ORDER BY occurred_at")) {
            while (rows.next()) {
                found.put(rows.getString("merchant"), rows.getString("time_slot"));
            }
        }
        return found;
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(env("DB_URL"), env("DB_USER"), env("DB_PASSWORD"));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다. CI와 07 §3의 로컬 설정을 보십시오.");
        }
        return value;
    }
}
