# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A collection of [boilr](https://github.com/Ilyes512/boilr) project templates. Each top-level folder (`kotlin/`, `clojure/`) is one template. Templates are rendered using Go `text/template` syntax.

## Template structure

Every template follows this layout:

```
<template-name>/
├── project.json     ← variable definitions (stays outside output)
└── template/        ← everything inside is copied and rendered
```

`project.json` rules:
- Scalar values → prompt default
- Array values → multiple-choice prompt
- Use lowercase snake_case keys (e.g. `app_name`, `stack_profile`)

## Go template syntax used in template files and file/directory names

```
{{app_name}}                                         value substitution
{{if eq stack_profile "cli"}}...{{end}}              conditional
{{if or (eq stack_profile "web") (eq stack_profile "web-db")}}...{{end}}
{{range template_type}}- {{.}}{{end}}                loop
{{- ... -}}                                          whitespace trimming
```

Both file contents **and** directory/file names support template syntax. For example, `src/{{tld}}/{{author}}/{{app_name}}/Main.kt` is a valid path inside `template/`.

## Using templates locally

```bash
# Register templates
boilr template save ./kotlin kotlin
boilr template save ./clojure clojure

# Generate a project
boilr template use kotlin ~/Workspace/my-kotlin-app
```

## Kotlin template

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---------|-------------------|
| `default` | Log4j (API + Kotlin API + Core), JUnit 5, AssertK, MockK |
| `cli` | `default` + Clikt + Mordant |
| `web` | `default` + Ktor (Netty, content-negotiation, serialization) + Log4j SLF4J |
| `db` | `default` + Exposed + HikariCP + Flyway + PostgreSQL driver |
| `web-db` | `web` + `db` combined |

`Main.kt` adapts to the profile: `web`/`web-db` add `ServerModule`, `db`/`web-db` add `DatabaseModule`. Prefer `stack_profile` for stable bundles; add one-off libraries via manual Gradle edits after generation.

**Build/test commands (run from a generated project, not this repo):**

```bash
gradle build
gradle test
gradle run
```

**Versions (defined in `kotlin/project.json`):** Gradle 8.12.1, Kotlin 2.1.10, JUnit 5.10.0.

## Kotlin microservice template

Opinionated Spring Boot 3.x microservice template in Kotlin. Common bundle (every profile): Spring Boot Web + Actuator, Micrometer + Prometheus, OpenTelemetry SDK + OTLP exporter, Log4j2 with `JsonTemplateLayout` (Logback excluded), AWS SDK v2 SNS publisher and SQS poller, multi-stage `Dockerfile` at the project root (production runtime image), and a `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS) and an OpenTelemetry collector for local development.

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---------|-------------------|
| `default` | none — HTTP + messaging only |
| `relational-db` | Spring Data JPA + Hibernate, Flyway (core + `flyway-database-postgresql`), PostgreSQL JDBC driver, Testcontainers Postgres for integration tests. Adds a `postgres:{{postgres_image_tag}}` service to the compose stack with `pg_isready` healthcheck; the `app` service waits on it. Ships a sample `@Entity`, `JpaRepository`, `V1__init.sql` migration, and a Testcontainers-backed integration test. |
| `nosql-cache` | **Raw** MongoDB Java sync driver (`mongodb-driver-sync`, **not** Spring Data MongoDB), Mongock (`mongock-springboot-v3` + `mongodb-sync-v4-driver`) for migrations, Spring Data Redis (Lettuce) for a `StringRedisTemplate` cache-aside helper, Testcontainers MongoDB + generic Redis container for integration tests. Adds `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services to the compose stack with healthchecks; the `app` service waits on both. Ships a sample `data class`, repository wrapping a `MongoCollection`, a Mongock `ChangeUnit`, a `SampleCache`, a cache-aside `SampleDocumentService`, a hand-wired `MongoHealthIndicator`, and a Testcontainers integration test exercising the full cache-aside flow. **Deliberate asymmetry**: raw Mongo driver but Spring Data Redis — the Mongo abstractions aren't worth their cost, Redis's are. Mongo config is under `app.mongo.*` (not `spring.data.mongodb.*`) because Spring Data Mongo auto-config is intentionally absent. |

The three profiles are mutually exclusive. Future profiles extend the same `stack_profile` array rather than introducing a new prompt.

**Prompts** (`kotlin-microservice/project.json`): `tld`, `author`, `app_name`, `version`, `java_version`, `stack_profile`, plus version pins (`spring_boot_version`, `kotlin_version`, `gradle_version`, `aws_sdk_version`, `otel_version`, `log4j_version`, `micrometer_version`, `testcontainers_version`, `flyway_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, `mongock_version`). Bump versions in one place by editing `project.json`.

**Register and use:**

```bash
boilr template save ./kotlin-microservice kotlin-microservice
boilr template use kotlin-microservice ~/Workspace/my-svc
```

**Bootstrap the wrapper after generating** (the template ships only `gradle-wrapper.properties`):

```bash
cd ~/Workspace/my-svc
gradle wrapper --gradle-version <gradle_version>
./gradlew bootRun
```

**Run the full local stack:**

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

The compose stack and its supporting configs (LocalStack init, OTel collector) live under `local/docker/`. The root-level `Dockerfile` and `.dockerignore` are intentionally kept at the project root because they are production artifacts, not local-dev assets.

This brings up the app, LocalStack (with an init script that creates the `<app_name>-events` topic + queue and subscribes them), and an OTel collector that prints spans to stdout.

## Clojure template

Minimal template — single `default` profile, Leiningen project, Clojure only.

**Build/test commands (run from a generated project):**

```bash
lein test
lein repl
lein uberjar
```

## Adding a new template

1. Create `<name>/project.json` and `<name>/template/` with your files.
2. Use the same common keys (`tld`, `author`, `app_name`, `version`) where applicable.
3. Register locally with `boilr template save ./<name> <name>` and verify with `boilr template use <name> /tmp/test-output`.
