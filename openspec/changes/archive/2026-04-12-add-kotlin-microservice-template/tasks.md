## 1. Scaffold the template directory

- [x] 1.1 Create `kotlin-microservice/` at the repo root with an empty `template/` subdirectory
- [x] 1.2 Write `kotlin-microservice/project.json` defining `tld`, `author`, `app_name`, `version`, `java_version`, plus version pins for `spring_boot_version`, `kotlin_version`, `gradle_version`, `aws_sdk_version`, `otel_version`, `log4j_version`
- [x] 1.3 Verify scaffolding with `boilr template save ./kotlin-microservice kotlin-microservice` and `boilr template list`

## 2. Gradle build skeleton

- [x] 2.1 Add `template/build.gradle.kts` with Kotlin JVM, Spring Boot, and Spring dependency-management plugins, version pins read from `project.json`, and a Java toolchain set to `{{java_version}}`
- [x] 2.2 Add `template/settings.gradle.kts` with `rootProject.name = "{{app_name}}"`
- [x] 2.3 Add `template/gradle.properties` with sane Kotlin/JVM defaults
- [x] 2.4 Vendor `template/gradle/wrapper/gradle-wrapper.properties` only (option (b) per implementation note); rendered README documents the one-time `gradle wrapper --gradle-version <pin>` bootstrap step that the user must run after generation
- [x] 2.5 Exclude `spring-boot-starter-logging` from `spring-boot-starter` and add `spring-boot-starter-log4j2`
- [x] 2.6 Add Spring Boot starters: `web`, `actuator`; and dependencies: `micrometer-registry-prometheus`, OpenTelemetry BOM + `opentelemetry-spring-boot-starter` + `opentelemetry-exporter-otlp`, AWS SDK v2 BOM + `sns` + `sqs`
- [x] 2.7 Add test dependencies: `spring-boot-starter-test` (excluding JUnit Vintage), AssertK, MockK, Testcontainers BOM + `localstack` module

## 3. Application skeleton and config

- [x] 3.1 Create `template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/Application.kt` with a `@SpringBootApplication`-annotated class and `main` function calling `runApplication<Application>(*args)`
- [x] 3.2 Create `template/src/main/resources/application.yml` with `spring.application.name: {{app_name}}`, `management.endpoints.web.exposure.include: health,info,prometheus`, `management.endpoint.health.probes.enabled: true`, server port `8080`
- [x] 3.3 Create `template/src/main/resources/application-local.yml` with LocalStack endpoint, region `us-east-1`, dummy creds, and the default SNS topic / SQS queue names
- [x] 3.4 Create `template/src/main/resources/log4j2.xml` using `JsonTemplateLayout` (ECS template) writing one JSON object per line to stdout

## 4. Observability wiring

- [x] 4.1 Add OTel config in `application.yml` setting default service name to `{{app_name}}` and OTLP endpoint to `http://localhost:4317`, both overridable via `OTEL_SERVICE_NAME` / `OTEL_EXPORTER_OTLP_ENDPOINT`
- [x] 4.2 Confirm Actuator liveness/readiness probes return `UP` and Prometheus endpoint serves metrics in a smoke run — verified live in task 9.3 (both probes returned `{"status":"UP"}`, `/actuator/prometheus` served 47 `jvm_*` metrics)

## 5. AWS SNS/SQS integration

- [x] 5.1 Create `template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/messaging/AwsClientsConfig.kt` exposing `SnsClient` and `SqsClient` beans built from configurable region, endpoint override, and credentials provider
- [x] 5.2 Create `messaging/SnsPublisher.kt` — a thin Spring component wrapping `SnsClient.publish` against a configured topic ARN
- [x] 5.3 Create `messaging/SqsPoller.kt` — a `@Scheduled` poll loop using `ReceiveMessageRequest` (long polling) that dispatches to a `MessageHandler` interface and deletes messages on success
- [x] 5.4 Create a sample `messaging/SampleMessageHandler.kt` that logs the received body at INFO
- [x] 5.5 Add `@ConfigurationProperties`-bound class `messaging/AwsMessagingProperties.kt` holding region, endpoint override, topic ARN, queue URL, and credentials
- [x] 5.6 Bind defaults in `application-local.yml` to the LocalStack-created topic and queue

## 6. Container and compose assets

- [x] 6.1 Add `template/Dockerfile` — multi-stage: builder uses `eclipse-temurin:{{java_version}}-jdk` and runs `./gradlew bootJar`; runtime uses `eclipse-temurin:{{java_version}}-jre`, copies the jar, runs as non-root user `app`, `EXPOSE 8080`, `HEALTHCHECK` curling `/actuator/health/liveness`
- [x] 6.2 Add `template/.dockerignore` excluding `.gradle`, `build`, `*.iml`, `.idea`, etc.
- [x] 6.3 Add `template/docker-compose.yml` with services: `app` (build context `.`, env `SPRING_PROFILES_ACTIVE=local`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317`), `localstack` (`localstack/localstack:3.8`, `SERVICES=sns,sqs`, init script mount), `otel-collector` (`otel/opentelemetry-collector-contrib:0.110.0`, config mount)
- [x] 6.4 Add `template/localstack/init/init-aws.sh` that uses `awslocal` to create the SNS topic, create the SQS queue, and subscribe the queue to the topic; mark executable
- [x] 6.5 Add `template/otel-collector/config.yaml` with an OTLP receiver and a debug exporter so traces print to collector stdout

## 7. Tests in the rendered project

- [x] 7.1 Create `template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/ApplicationTests.kt` with a `@SpringBootTest` context-load smoke test
- [x] 7.2 Create `template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/messaging/SqsPollerIntegrationTest.kt` using Testcontainers `LocalStackContainer` to verify publish-then-consume round-trip

## 8. Repo-level documentation

- [x] 8.1 Add a "Kotlin microservice template" section to `CLAUDE.md` listing bundled deps, prompts, version-pin location, and `boilr template save` / `template use` commands; explicitly note no `stack_profile` in v1
- [x] 8.2 Update `README.md` to list `kotlin-microservice` alongside `kotlin` and `clojure` with a one-line description

## 9. End-to-end verification

- [x] 9.1 Ran `boilr template save -f ./kotlin-microservice kotlin-microservice` and `boilr template use kotlin-microservice /tmp/svc-test` with all defaults — render succeeded, zero unresolved `{{...}}` tokens in any rendered file or path
- [x] 9.2 In `/tmp/svc-test` ran `gradle wrapper --gradle-version 8.12.1` then `./gradlew build -x test` — `BUILD SUCCESSFUL`, fat jar produced at `build/libs/myservice-0.1.0.jar`. Fixed a `kotlinOptions` deprecation (migrated to `compilerOptions` DSL) on the second iteration; second build was clean.
- [x] 9.3 In `/tmp/svc-test` ran `./gradlew bootRun` — Spring Boot started in 0.98s, Tomcat on :8080, JSON logs via Log4j2 ECS layout. `GET /actuator/health/liveness` → 200 `{"status":"UP"}`, `GET /actuator/health/readiness` → 200 `{"status":"UP"}`, `GET /actuator/prometheus` → 200 with 47 `jvm_*` metric lines.
- [x] 9.4 In `/tmp/svc-test` ran `docker build -t svc-test:dev .` — `BUILD SUCCESSFUL`. `docker run --rm --entrypoint id svc-test:dev -u` printed `999` (non-root). Note: Dockerfile was switched from the wrapper-based builder to `gradle:{{gradle_version}}-jdk{{java_version}}` (W1 fix from /opsx:verify) so `docker build` no longer requires the user to run `gradle wrapper` first.
- [x] 9.5 In `/tmp/svc-test` ran `docker compose up -d --build` — all three services (app, localstack, otel-collector) reached healthy. `awslocal sns list-topics` / `awslocal sqs list-queues` / `awslocal sns list-subscriptions` confirmed the init script created `arn:aws:sns:us-east-1:000000000000:myservice-events`, the matching SQS queue, and the SNS→SQS subscription. `awslocal sns publish --message hello-from-test` produced log `Received SQS message: hello-from-test` from `SampleMessageHandler` in `docker compose logs app`, JSON-formatted via Log4j2.
- [x] 9.6 With the stack up, hit `/actuator/health` three times — `docker compose logs otel-collector` showed 14 OTLP `ResourceSpans` with `service.name: Str(myservice)`, confirming the OTel SDK exports spans through the collector.
