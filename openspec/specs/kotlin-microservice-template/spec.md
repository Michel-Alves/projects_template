# kotlin-microservice-template Specification

## Purpose
TBD - created by archiving change add-kotlin-microservice-template. Update Purpose after archive.
## Requirements
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
The `project.json` SHALL define the prompts `tld`, `author`, `app_name`, `version`, `java_version`, and `stack_profile` using lowercase snake_case keys, matching the convention used by the sibling `kotlin/` and `clojure/` templates. Each prompt MUST have a sensible scalar default. The `stack_profile` prompt SHALL be defined as a JSON array of allowed values, with `default` listed first (so it is selected when the user accepts the default) and `relational-db` listed second. Future profiles (e.g. `nosql-db`) MUST be added to this same array rather than introduced as new prompts.

#### Scenario: Prompts are exposed to the user
- **WHEN** a user runs `boilr template use kotlin-microservice /tmp/svc`
- **THEN** boilr prompts for `tld`, `author`, `app_name`, `version`, `java_version`, and `stack_profile` in some order, each showing its default
- **AND** the `stack_profile` prompt presents `default` and `relational-db` as the only choices, with `default` as the default selection

#### Scenario: stack_profile prompt is an array with default first
- **WHEN** a reader inspects `kotlin-microservice/project.json`
- **THEN** the `stack_profile` key is an array whose first element is `"default"` and whose second element is `"relational-db"`
- **AND** no other prompt in `project.json` shadows or overrides `stack_profile`

#### Scenario: Default profile produces output equivalent to the pre-profile template
- **WHEN** the template is generated with `stack_profile=default` and all other defaults accepted
- **THEN** the rendered project's Kotlin source tree, `build.gradle.kts`, `application.yml`, `application-local.yml`, `Dockerfile`, and `local/docker/docker-compose.yml` contain no JPA, Hibernate, Flyway, Postgres driver, Testcontainers Postgres, or `postgres` compose service references
- **AND** `./gradlew dependencies` reports no `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`, or `org.testcontainers:postgresql` on any configuration
- **AND** documentation files such as the rendered `README.md` MAY mention `relational-db` as a discoverability hint (e.g. in a "Trade-offs" section pointing users at the alternative profile) — documentation is explicitly exempt from this byte-equivalence requirement

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
The generated project SHALL include a `docker-compose.yml` at `local/docker/docker-compose.yml` (not at the project root) that defines at minimum three services: the application (built from the root-level `Dockerfile` via a `build.context` of `../..` so the Dockerfile and the source tree are part of the build context), `localstack` configured with `SERVICES=sns,sqs`, and an OpenTelemetry collector. The compose file's volume mounts for the LocalStack init scripts and the OTel collector config SHALL resolve to `local/docker/localstack/init/` and `local/docker/otel-collector/config.yaml` respectively — i.e. the `localstack/` and `otel-collector/` directories MUST live alongside the compose file, not at the project root. LocalStack MUST run an init script on startup that creates the configured SNS topic and SQS queue and subscribes the queue to the topic. The application service MUST set `SPRING_PROFILES_ACTIVE=local` and point its OTel exporter at the collector. The root-level `Dockerfile` and `.dockerignore` MUST remain at the project root because they are production artifacts, not local-dev assets. When `stack_profile=relational-db`, the compose file SHALL additionally define a `postgres` service (image `postgres:16-alpine`, named volume for data persistence, env-var-supplied `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` defaulting to `<app_name>` for all three, healthcheck via `pg_isready`), and the `app` service's `depends_on` block SHALL gain `postgres: { condition: service_healthy }`. When `stack_profile=default`, the compose file SHALL contain neither the `postgres` service block nor the `postgres` `depends_on` entry.

#### Scenario: Compose stack starts cleanly from the new path
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up --build` from the project root of a freshly generated project
- **THEN** all services for the selected `stack_profile` reach a healthy state and the application logs show a successful Spring Boot startup banner

#### Scenario: SNS topic and SQS queue exist after startup
- **WHEN** the compose stack is running
- **THEN** the init script has created the configured SNS topic and SQS queue inside LocalStack and the queue is subscribed to the topic, verifiable via `awslocal sns list-topics` and `awslocal sqs list-queues`

#### Scenario: Compose file references the root Dockerfile via relative build context
- **WHEN** a reader inspects `local/docker/docker-compose.yml`
- **THEN** the `app` service's `build:` block declares `context: ../..` and `dockerfile: Dockerfile`, so `docker compose build` resolves the production `Dockerfile` and the `src/` tree from the project root

#### Scenario: Local-dev assets are colocated under local/docker/
- **WHEN** a reader runs `tree local/docker` in a freshly generated project
- **THEN** the output contains `docker-compose.yml`, `localstack/init/init-aws.sh`, and `otel-collector/config.yaml` and no other files outside the `local/docker/` subtree are required for `docker compose up`

#### Scenario: Production Dockerfile stays at the project root
- **WHEN** a reader lists the project root of a freshly generated project
- **THEN** `Dockerfile` and `.dockerignore` are present at the root and are NOT duplicated under `local/docker/`

#### Scenario: Postgres service is added under relational-db
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the rendered `local/docker/docker-compose.yml` contains a `postgres` service with image `postgres:16-alpine`, a `pg_isready`-based healthcheck, and a named volume for data persistence
- **AND** the `app` service's `depends_on` block contains `postgres: { condition: service_healthy }`

#### Scenario: Postgres service is absent under default
- **WHEN** the template is generated with `stack_profile=default`
- **THEN** the rendered `local/docker/docker-compose.yml` contains no `postgres` service block and the `app` service's `depends_on` block does not mention `postgres`
- **AND** `docker compose -f local/docker/docker-compose.yml config` parses cleanly with no warnings

#### Scenario: Postgres healthcheck gates app startup under relational-db
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up` against a `relational-db`-rendered project
- **THEN** the `app` service starts only after the `postgres` service reports `(healthy)`, and the application's startup logs show Flyway running `V1__init.sql` against the container before Hibernate validates the schema

### Requirement: Repository documentation describes the new template
`CLAUDE.md` and `README.md` SHALL be updated to describe the `kotlin-microservice/` template, its bundled dependencies, the prompts it exposes, and the boilr commands for registering and using it. The documentation MUST explicitly note that this template is single-shape (no `stack_profile`) and lists Spring Boot, Actuator, Micrometer/Prometheus, OpenTelemetry, Log4j2 JSON logging, AWS SDK v2 SNS/SQS, and the docker-compose LocalStack stack as bundled. The `CLAUDE.md` "Kotlin microservice template" section MUST show the compose invocation as `docker compose -f local/docker/docker-compose.yml up --build` to match the new layout. The rendered template's `README.md` MUST document the same invocation in its "Run the full stack with docker-compose" section and MUST include a one-paragraph explanation that the root-level `Dockerfile` is the production artifact while everything under `local/docker/` is local-dev only.

#### Scenario: CLAUDE.md documents the template
- **WHEN** a reader opens `CLAUDE.md`
- **THEN** there is a "Kotlin microservice template" section listing the bundled dependencies and the `boilr template save` / `boilr template use` commands for it
- **AND** the section shows `docker compose -f local/docker/docker-compose.yml up --build` as the command to bring up the local stack

#### Scenario: README.md lists the template
- **WHEN** a reader opens `README.md`
- **THEN** the template index includes `kotlin-microservice` alongside `kotlin` and `clojure` with a one-line description

#### Scenario: Rendered README explains the prod-vs-local layout
- **WHEN** a reader opens the rendered `README.md` of a freshly generated project
- **THEN** the "Run the full stack with docker-compose" section shows the `docker compose -f local/docker/docker-compose.yml up --build` invocation
- **AND** the section contains a paragraph explaining that the root `Dockerfile` is the production runtime image while everything under `local/docker/` is for local development only

### Requirement: relational-db profile bundles Spring Data JPA, PostgreSQL, and Flyway
When the user selects `stack_profile=relational-db`, the generated project SHALL include Spring Data JPA (`spring-boot-starter-data-jpa`, with Hibernate as the JPA provider), the PostgreSQL JDBC driver (`org.postgresql:postgresql`, version managed by the Spring Boot BOM), Flyway (`org.flywaydb:flyway-core` AND `org.flywaydb:flyway-database-postgresql` — both modules are required for Postgres on Flyway 10+), and the Testcontainers PostgreSQL module (`org.testcontainers:postgresql`) on the test classpath. When the user selects `stack_profile=default`, NONE of these dependencies SHALL appear on the build classpath. The Hibernate `ddl-auto` setting SHALL be `validate` so schema/entity drift fails fast at startup, and Spring Boot `open-in-view` SHALL be set to `false`.

#### Scenario: relational-db classpath includes JPA, Postgres driver, and Flyway
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the rendered `build.gradle.kts` declares dependencies on `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, and `org.flywaydb:flyway-database-postgresql`
- **AND** the rendered `build.gradle.kts` declares `org.testcontainers:postgresql` on `testImplementation`

#### Scenario: default profile does not include any persistence dependencies
- **WHEN** the template is generated with `stack_profile=default`
- **THEN** the rendered `build.gradle.kts` does NOT contain the strings `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`, or `testcontainers:postgresql`
- **AND** running `./gradlew dependencies` against the rendered project produces a dependency tree byte-identical to the `default`-profile output before this change

#### Scenario: Hibernate ddl-auto is validate
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the rendered `application.yml` contains `spring.jpa.hibernate.ddl-auto: validate` and `spring.jpa.open-in-view: false`

### Requirement: relational-db profile ships a sample entity, repository, and Flyway migration
When `stack_profile=relational-db`, the generated project SHALL include a single sample `@Entity` class, a `JpaRepository`-based repository over that entity, a Flyway migration that creates the entity's table, and a Testcontainers-backed integration test that exercises a save→read round-trip. The sample entity SHALL be intentionally minimal (an autogenerated `Long` id and a single `name: String` field) so teams can replace it inside an hour. None of these files SHALL be rendered when `stack_profile=default`.

#### Scenario: Sample entity, repository, and migration exist for relational-db
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/SampleEntity.kt` exists, declares `package <tld>.<author>.<app_name>.persistence`, and is annotated with `@Entity`
- **AND** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/SampleRepository.kt` exists and declares an interface extending `JpaRepository<SampleEntity, Long>`
- **AND** the file `src/main/resources/db/migration/V1__init.sql` exists and contains a `CREATE TABLE` statement for the entity's table

#### Scenario: Persistence files do NOT render under default
- **WHEN** the template is generated with `stack_profile=default`
- **THEN** none of the files under `src/main/kotlin/<tld>/<author>/<app_name>/persistence/`, `src/main/resources/db/migration/`, or `src/test/kotlin/<tld>/<author>/<app_name>/persistence/` are rendered (a recursive `find -type f` inside each of those directories returns zero results)
- **AND** the parent directories themselves MAY exist as empty directories as an accepted side-effect of the content-level conditional rendering strategy (boilr creates the path on disk but writes no file because the trimmed conditional produces empty content); these empty directories are harmless to the Kotlin compiler, Gradle, and Flyway

#### Scenario: Integration test exercises a real Postgres via Testcontainers
- **WHEN** a user runs `./gradlew test` against a freshly generated `relational-db` project with a running Docker daemon
- **THEN** `SampleRepositoryIntegrationTest` boots a `PostgreSQLContainer`, wires its connection details into the Spring context via `@DynamicPropertySource`, lets Flyway run `V1__init.sql` against the container, saves a `SampleEntity`, reads it back by id, and asserts equality on the `name` field

### Requirement: relational-db local profile points Spring datasource at the compose Postgres
When `stack_profile=relational-db`, the rendered `application-local.yml` SHALL declare a `spring.datasource.*` block whose URL, username, and password resolve to the Postgres service defined in `local/docker/docker-compose.yml` (host `postgres`, database `<app_name>`, user `<app_name>`, password `<app_name>` — all overridable through standard Spring environment variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). The base `application.yml` SHALL NOT contain a hardcoded datasource URL — only the local profile and env vars supply connection details.

#### Scenario: Local profile datasource targets the compose postgres
- **WHEN** a user runs the relational-db service with `SPRING_PROFILES_ACTIVE=local` and no other overrides against the bundled compose stack
- **THEN** Spring connects to `jdbc:postgresql://postgres:5432/<app_name>` (or equivalent host-port resolution from inside the compose network) with user `<app_name>` and password `<app_name>`

#### Scenario: Base application.yml has no hardcoded datasource URL
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the rendered `application.yml` contains a `spring.jpa.*` block and a `spring.flyway.*` block, but does NOT contain a `spring.datasource.url` value
- **AND** the rendered `application-local.yml` is the only resource file that supplies a `spring.datasource.url`
