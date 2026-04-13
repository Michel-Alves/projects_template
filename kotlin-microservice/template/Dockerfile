# syntax=docker/dockerfile:1.7
FROM gradle:{{gradle_version}}-jdk{{java_version}} AS builder
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:{{java_version}}-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
