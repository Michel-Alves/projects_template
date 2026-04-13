## Why

The repository currently ships a general-purpose `kotlin/` template whose `web` profile targets Ktor and assumes a small, self-contained service. Teams starting a new Spring Boot microservice — the dominant stack in most JVM shops — have to bolt on observability, containerization, and AWS messaging by hand every time. A dedicated, opinionated template removes that repeated setup and encodes the conventions we want new services to follow from day one.

## What Changes

- Add a new top-level boilr template `kotlin-microservice/` alongside `kotlin/` and `clojure/`.
- Generated projects use **Spring Boot** (Kotlin, Gradle Kotlin DSL) as the application framework.
- Bundle observability out of the box: Spring Boot Actuator for `/health` and `/ready`, Micrometer + Prometheus registry for `/metrics`, and OpenTelemetry SDK with an OTLP exporter for distributed tracing.
- Bundle structured JSON logging via Log4j2 with a JSON layout, replacing the default Logback.
- Bundle AWS messaging: AWS SDK v2 SNS and SQS clients with a sample publisher, a `@SqsListener`-style consumer, and LocalStack wiring for local development.
- Ship a multi-stage `Dockerfile` and a `docker-compose.yml` that brings up the service plus LocalStack (SNS/SQS) and an OTel collector for local runs.
- Provide a `project.json` exposing the common keys (`tld`, `author`, `app_name`, `version`) plus a `java_version` prompt; no `stack_profile` for the first iteration — the template is single-shape on purpose.
- Update `CLAUDE.md` and `README.md` to document the new template, its dependencies, and how to register and use it with boilr.

## Capabilities

### New Capabilities
- `kotlin-microservice-template`: Defines the layout, prompts, and rendered output of the new boilr template, including the Spring Boot application skeleton, observability stack, AWS SNS/SQS integration, and container/compose assets.

### Modified Capabilities
<!-- None — existing templates are untouched. Only repo-level docs change, which is captured under Impact. -->

## Impact

- **New files**: `kotlin-microservice/project.json` and the entire `kotlin-microservice/template/` tree (Gradle build, `Application.kt`, config, sample SNS/SQS publisher and consumer, `Dockerfile`, `docker-compose.yml`, `application.yml`, Log4j2 JSON config, OTel bootstrap).
- **Docs**: `CLAUDE.md` gains a "Kotlin microservice template" section; `README.md` lists the new template and its register/use commands.
- **Dependencies introduced in generated projects** (not in this repo itself): Spring Boot starter-web, starter-actuator, Micrometer Prometheus registry, OpenTelemetry SDK + OTLP exporter + Spring Boot starter, Log4j2 + jackson JSON layout, AWS SDK v2 (`sns`, `sqs`), LocalStack via docker-compose, JUnit 5, AssertK, MockK, Testcontainers (LocalStack module) for integration tests.
- **No changes** to the existing `kotlin/` or `clojure/` templates; users continue to opt into whichever template they want via `boilr template use`.
- **Versions to pin** (decided in design): Spring Boot, Kotlin, Gradle, AWS SDK v2, OpenTelemetry BOM, Log4j2, Testcontainers.
