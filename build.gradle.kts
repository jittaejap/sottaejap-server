plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "kr.sottaejap"
version = "0.0.1-SNAPSHOT"
description = "소때잡 서버 — 인증 · 저장/조회 API · 파싱 · 규칙 엔진 · AI 내부 API · Flyway 소유"

// 버전 정본은 sottaejap-docs/07_기술스택_레포구성.md §1 (v1.5). Boot BOM이 관리하는 것은 적지 않는다.
val springdocVersion = "3.1.0"
val jjwtVersion = "0.13.0"
val poiVersion = "5.5.1"
val webPushVersion = "5.1.1"
val bouncyCastleVersion = "1.78.1"

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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API 문서 — Boot 4.1은 springdoc 3.1.x (07 §1 · E-35)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // XLSX 업로드 — 05 §2. Boot 미관리
    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    // Web Push — Boot 미관리. VAPID JWT 서명과 AES128GCM 본문 암호화 때문에 직접 짜지 않는다.
    // 이 라이브러리에서 쓰는 것은 암호화와 서명뿐이고 전송은 AiClient와 같은 JDK HttpClient로 한다.
    // 그래서 딸려 오는 HTTP 스택 셋(Apache async · AHC/Netty · CLI용 jcommander)은 전부 제외한다.
    implementation("nl.martijndwars:web-push:$webPushVersion") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "org.asynchttpclient")
        exclude(group = "com.beust", module = "jcommander")
    }
    // web-push의 POM이 빠뜨린다. AbstractPushService.encrypt가 BouncyCastle 타입을 직접 받는다.
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    // web-push의 Base64Encoder가 쓴다. 원래는 위에서 제외한 httpclient를 타고 딸려 오던 것이다.
    implementation("commons-codec:commons-codec")

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
