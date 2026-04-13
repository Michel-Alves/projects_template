## ADDED Requirements

### Requirement: Template registers with boilr
The repository SHALL contain a top-level directory `kotlin-microservice/` that boilr can register and use without errors. The directory MUST contain a `project.json` at its root and a `template/` subdirectory holding everything that gets rendered into the generated project.

#### Scenario: Registering the template succeeds
- **WHEN** a user runs `boilr template save ./kotlin-microservice kotlin-microservice` from the repo root
- **THEN** boilr reports success and the template is listed by `boilr template list`

#### Scenario: Generating into an empty directory succeeds
- **WHEN** a user runs `boilr template use kotlin-microservice /tmp/svc` and accepts every prompt default
- **THEN** boilr renders all files into `/tmp/svc` and exits with status 0
- **AND** no rendered file or directory name contains an unresolved `{{...}}` placeholder

### Requirement: Template prompts for the common project keys
The `project.json` SHALL define the prompts `tld`, `author`, `app_name`, `version`, and `java_version` using lowercase snake_case keys, matching the convention used by the sibling `kotlin/` and `clojure/` templates. Each prompt MUST have a sensible scalar default.

#### Scenario: Prompts are exposed to the user
- **WHEN** a user runs `boilr template use kotlin-microservice /tmp/svc`
- **THEN** boilr prompts for `tld`, `author`, `app_name`, `version`, and `java_version` in that order, each showing its default

#### Scenario: No stack profile prompt in v1
- **WHEN** the template is generated
- **THEN** the user is NOT prompted for a `stack_profile` value
- **AND** `project.json` MUST NOT define a `stack_profile` key

### Requirement: Generated source tree is namespaced under the user's package
Generated Kotlin source and test files SHALL live under `src/main/kotlin/<tld>/<author>/<app_name>/` and `src/test/kotlin/<tld>/<author>/<app_name>/` respectively, where the path segments are taken from the prompt values. Every Kotlin file in those trees MUST declare a `package <tld>.<author>.<app_name>[.subpackage]` matching its location.

#### Scenario: Default values produce a valid package layout
- **WHEN** the user accepts all defaults (e.g. `tld=com`, `author=example`, `app_name=svc`)
- **THEN** the entry point exists at `src/main/kotlin/com/example/svc/Application.kt`
- **AND** the file declares `package com.example.svc`

### Requirement: Generated project builds and runs after one-time wrapper bootstrap
A freshly generated project SHALL build and start after a single, documented bootstrap step. The template SHALL ship `gradle/wrapper/gradle-wrapper.properties` pinned to the templated Gradle version, and the rendered `README.md` SHALL document running `gradle wrapper --gradle-version <gradle_version>` once after generation to materialize `gradlew` and `gradle-wrapper.jar`. After that bootstrap, no further manual edits SHALL be required to build or run the project. The build MUST use Gradle Kotlin DSL and the Java toolchain version selected by the `java_version` prompt.

#### Scenario: Wrapper bootstrap and Gradle build succeed on a fresh checkout
- **WHEN** a user runs `gradle wrapper --gradle-version <gradle_version>` followed by `./gradlew build` inside a freshly generated project
- **THEN** both commands complete successfully and `./gradlew build` produces a runnable Spring Boot fat jar under `build/libs/`

#### Scenario: Application boots with default config
- **WHEN** a user runs `./gradlew bootRun` inside a freshly bootstrapped project with no environment overrides
- **THEN** the Spring Boot application starts and binds to port 8080
- **AND** `GET http://localhost:8080/actuator/health/liveness` returns HTTP 200 with status `UP`

### Requirement: Spring Boot application skeleton
The generated project SHALL be a Spring Boot 3.x application written in Kotlin, with a single `@SpringBootApplication`-annotated entry point and an `application.yml` (not `application.properties`) under `src/main/resources/`. The application MUST set `spring.application.name` to the value of the `app_name` prompt.

#### Scenario: Entry point is annotated and runnable
- **WHEN** the template is generated
- **THEN** `Application.kt` contains a class annotated with `@SpringBootApplication` and a `main` function that calls `runApplication<Application>(*args)`

#### Scenario: Application name is templated
- **WHEN** the template is generated with `app_name=orders`
- **THEN** `application.yml` contains `spring.application.name: orders`

### Requirement: Health, readiness, and metrics endpoints exposed
The generated project SHALL expose Spring Boot Actuator endpoints for health, liveness, readiness, and Prometheus-format metrics. Liveness and readiness probes MUST be enabled. The Prometheus scrape endpoint MUST be reachable without authentication on the default management port.

#### Scenario: Liveness and readiness probes return 200
- **WHEN** the application is running with default config
- **THEN** `GET /actuator/health/liveness` returns HTTP 200 with body containing `"status":"UP"`
- **AND** `GET /actuator/health/readiness` returns HTTP 200 with body containing `"status":"UP"`

#### Scenario: Prometheus endpoint serves metrics
- **WHEN** the application is running with default config
- **THEN** `GET /actuator/prometheus` returns HTTP 200 with a `text/plain` body containing at least one `# HELP` line and one `jvm_` metric

### Requirement: OpenTelemetry tracing wired with OTLP exporter
The generated project SHALL include the OpenTelemetry Spring Boot starter and an OTLP trace exporter. The OTLP endpoint and the OTel service name MUST be overridable via the standard `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_SERVICE_NAME` environment variables, with a default service name equal to the `app_name` prompt and a default endpoint of `http://localhost:4317`.

#### Scenario: Default service name matches app name
- **WHEN** the template is generated with `app_name=orders`
- **THEN** `application.yml` (or equivalent OTel config) sets the default service name to `orders`

#### Scenario: Endpoint is overridable by env var
- **WHEN** the application is started with `OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4317`
- **THEN** the OTel SDK exports spans to `http://collector:4317` instead of the default `localhost:4317`

### Requirement: Structured JSON logging via Log4j2
The generated project SHALL use Log4j2 instead of Logback for application logging, and the default appender MUST emit one JSON object per log line on stdout. The Logback starter MUST be excluded from the Spring Boot dependencies.

#### Scenario: Logback starter is excluded
- **WHEN** the template is generated
- **THEN** the Gradle build excludes `spring-boot-starter-logging` from `spring-boot-starter` and depends on `spring-boot-starter-log4j2`

#### Scenario: Log lines are valid JSON
- **WHEN** the running application emits any log line at INFO level
- **THEN** the line written to stdout parses as a single JSON object containing at least the fields `@timestamp`, `level`, `message`, and `logger`

### Requirement: AWS SNS publisher and SQS consumer wired with AWS SDK v2
The generated project SHALL include a Spring-managed SNS publisher bean and an SQS poller bean built on AWS SDK v2 (`software.amazon.awssdk:sns` and `:sqs`). Endpoint, region, topic ARN, and queue URL MUST be configurable through `application.yml` and overridable by environment variables. A `local` Spring profile MUST default these values to a LocalStack endpoint with dummy credentials so the service can run against the bundled docker-compose stack with no extra setup.

#### Scenario: Publisher bean exists and is wired
- **WHEN** the application context starts
- **THEN** a singleton bean implementing the project's SNS publisher type is registered and uses an `SnsClient` constructed from configurable region, credentials, and endpoint override

#### Scenario: Poller bean exists and consumes messages
- **WHEN** the application is running against a LocalStack SQS queue containing one message
- **THEN** the poller invokes its handler exactly once with the message body and deletes the message from the queue afterwards

#### Scenario: Local profile points at LocalStack
- **WHEN** the application is started with `SPRING_PROFILES_ACTIVE=local` and no other overrides
- **THEN** the SNS and SQS clients target endpoint `http://localhost:4566` with a static dummy credentials provider

### Requirement: Multi-stage Dockerfile produces a runnable image
The generated project SHALL include a multi-stage `Dockerfile` at the project root that builds the Spring Boot fat jar in a JDK stage and copies it into a JRE runtime stage based on `eclipse-temurin:<java_version>-jre`. The runtime stage MUST run as a non-root user, expose port 8080, and define a `HEALTHCHECK` that hits `/actuator/health/liveness`.

#### Scenario: Image builds successfully
- **WHEN** a user runs `docker build -t svc:dev .` inside a freshly generated project
- **THEN** the build completes successfully and produces an image tagged `svc:dev`

#### Scenario: Container runs as non-root
- **WHEN** a container is started from the built image
- **THEN** `docker exec <container> id -u` returns a non-zero UID

### Requirement: docker-compose stack brings up service, LocalStack, and OTel collector
The generated project SHALL include a `docker-compose.yml` at the project root that defines at minimum three services: the application (built from the local `Dockerfile`), `localstack` configured with `SERVICES=sns,sqs`, and an OpenTelemetry collector. LocalStack MUST run an init script on startup that creates the configured SNS topic and SQS queue and subscribes the queue to the topic. The application service MUST set `SPRING_PROFILES_ACTIVE=local` and point its OTel exporter at the collector.

#### Scenario: Compose stack starts cleanly
- **WHEN** a user runs `docker compose up` inside a freshly generated project
- **THEN** all three services reach a healthy state and the application logs show a successful Spring Boot startup banner

#### Scenario: SNS topic and SQS queue exist after startup
- **WHEN** the compose stack is running
- **THEN** the init script has created the configured SNS topic and SQS queue inside LocalStack and the queue is subscribed to the topic, verifiable via `awslocal sns list-topics` and `awslocal sqs list-queues`

### Requirement: Repository documentation describes the new template
`CLAUDE.md` and `README.md` SHALL be updated to describe the `kotlin-microservice/` template, its bundled dependencies, the prompts it exposes, and the boilr commands for registering and using it. The documentation MUST explicitly note that this template is single-shape (no `stack_profile`) and lists Spring Boot, Actuator, Micrometer/Prometheus, OpenTelemetry, Log4j2 JSON logging, AWS SDK v2 SNS/SQS, and the docker-compose LocalStack stack as bundled.

#### Scenario: CLAUDE.md documents the template
- **WHEN** a reader opens `CLAUDE.md`
- **THEN** there is a "Kotlin microservice template" section listing the bundled dependencies and the `boilr template save` / `boilr template use` commands for it

#### Scenario: README.md lists the template
- **WHEN** a reader opens `README.md`
- **THEN** the template index includes `kotlin-microservice` alongside `kotlin` and `clojure` with a one-line description
