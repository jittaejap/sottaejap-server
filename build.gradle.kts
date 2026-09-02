plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "kr.sottaejap"
version = "0.0.1-SNAPSHOT"
description = "소때잡 서버 — 인증 · 저장/조회 API · 파싱 · 규칙 엔진 · AI 내부 API · Flyway 소유"

// 버전 정본은 myDocs/07_기술스택_레포구성.md §1 (v1.5). Boot BOM이 관리하는 것은 적지 않는다.
val springdocVersion = "3.1.0"
val jjwtVersion = "0.13.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API 문서 — Boot 4.1은 springdoc 3.1.x (07 §1 · E-35)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // JWT — Boot 미관리
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Windows 기본 인코딩이 MS949라 소스·테스트 출력이 깨진다 (07 §5-3).
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    // 게이트 값을 입력으로 잡아, RUN_DB_INTEGRATION_TESTS를 켜고 다시 돌릴 때 test가 UP-TO-DATE로
    // 건너뛰어지지 않게 한다. 건너뛴 테스트가 초록불로 보이는 것을 막는다.
    inputs.property("dbIntegrationTests", System.getenv("RUN_DB_INTEGRATION_TESTS") ?: "false")
}
