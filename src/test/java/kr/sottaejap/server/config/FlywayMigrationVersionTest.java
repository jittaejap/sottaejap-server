package kr.sottaejap.server.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 마이그레이션 파일명이 V{n}__{설명}.sql 형식이고 버전이 겹치지 않는지 확인한다.
 * 두 브랜치가 같은 번호를 쓰면 병합 뒤에야 Flyway가 터지므로 여기서 먼저 잡는다.
 */
class FlywayMigrationVersionTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([0-9]+(?:\\.[0-9]+)*)__.+\\.sql$");

    @Test
    void versionedMigrations_haveUniqueVersions() throws IOException {
        Map<String, String> fileByVersion = new HashMap<>();
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(fileName -> registerVersion(fileByVersion, fileName));
        }
        assertTrue(fileByVersion.containsKey("1"), "V1__init.sql이 있어야 합니다");
    }

    private void registerVersion(Map<String, String> fileByVersion, String fileName) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(fileName);
        assertTrue(matcher.matches(), () -> "Invalid Flyway migration file name: " + fileName);
        String existing = fileByVersion.putIfAbsent(matcher.group(1), fileName);
        if (existing != null) {
            fail("Duplicate Flyway version V" + matcher.group(1) + ": " + existing + ", " + fileName);
        }
    }
}
