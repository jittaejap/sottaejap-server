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
# 앱과 DB(docker-compose의 TZ)를 같은 시간대로 맞춘다. 05 §0 날짜는 +09:00 오프셋이다.
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", "app.jar"]
