# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 소스보다 먼저 복사해서, 소스만 바뀔 때는 의존성 다운로드 레이어를 재사용한다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null 2>&1 || true

COPY src src
# 테스트는 CI가 따로 돌린다.
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
# 아래 HEALTHCHECK가 Spring Actuator를 두드릴 때 쓰는 최소 HTTP 클라이언트.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# 앱과 DB(docker-compose의 TZ)를 같은 시간대로 맞춘다. 05 §0 날짜는 +09:00 오프셋이다.
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
# 컨테이너가 살아 있는 것과 서버가 요청을 받는 것은 다르다. JVM은 떠 있는데 Flyway나 DB 연결이
# 막혀 있으면 /actuator/health가 503을 낸다 — 오케스트레이터가 그때 재시작·트래픽 차단을 결정한다.
# start-period 동안의 실패는 재시도로 세지 않으므로 기동이 느려도 unhealthy로 떨어지지 않는다.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", "app.jar"]
